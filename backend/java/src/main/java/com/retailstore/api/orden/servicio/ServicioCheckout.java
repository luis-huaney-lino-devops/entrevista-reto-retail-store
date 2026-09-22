package com.retailstore.api.orden.servicio;

import com.retailstore.api.carrito.dominio.Carrito;
import com.retailstore.api.carrito.dominio.EstadoCarrito;
import com.retailstore.api.carrito.dominio.ItemCarrito;
import com.retailstore.api.carrito.repositorio.CarritoRepositorio;
import com.retailstore.api.carrito.servicio.CalculadoraCarrito;
import com.retailstore.api.carrito.servicio.PrecioCarrito;
import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cupon.dominio.Cupon;
import com.retailstore.api.notificacion.servicio.AvisosDeStock;
import com.retailstore.api.orden.dominio.ItemOrden;
import com.retailstore.api.orden.dominio.Orden;
import com.retailstore.api.orden.dto.CrearOrdenPeticion;
import com.retailstore.api.orden.dto.OrdenDetalleRespuesta;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convierte un carrito en una orden. Es el checkout de la tienda.
 *
 * <p>Vive aparte de {@link ServicioOrden} —que consulta y mueve de estado—
 * porque son dos cosas distintas: aquí se <strong>crea</strong> el hecho, allí
 * se administra lo ya ocurrido. Mezclarlas pondría en la misma clase la única
 * operación que descuenta stock junto a las que solo leen.
 *
 * <p><strong>Todo ocurre en una transacción</strong> (RN-050). Si cualquier
 * paso falla, no queda nada a medias: ni stock descontado sin orden, ni orden
 * sin su cupón consumido, ni un carrito marcado como convertido que no
 * convirtió nada.
 */
@Service
@Transactional
public class ServicioCheckout {

    private final CarritoRepositorio carritos;
    private final OrdenRepositorio ordenes;
    private final ClienteRepositorio clientes;
    private final CalculadoraCarrito calculadora;
    private final GeneradorNumeroOrden numeros;
    private final AvisosDeStock avisos;

    public ServicioCheckout(CarritoRepositorio carritos, OrdenRepositorio ordenes,
                            ClienteRepositorio clientes, CalculadoraCarrito calculadora,
                            GeneradorNumeroOrden numeros, AvisosDeStock avisos) {
        this.carritos = carritos;
        this.ordenes = ordenes;
        this.clientes = clientes;
        this.calculadora = calculadora;
        this.numeros = numeros;
        this.avisos = avisos;
    }

    /**
     * @param idCliente el cliente autenticado, o {@code null} si compra como
     *                  invitado. Comprar no exige cuenta (RN-063).
     */
    public OrdenDetalleRespuesta crear(CrearOrdenPeticion peticion, Long idCliente) {
        Carrito carrito = carritoConfirmable(peticion.carritoId());

        // 1. Revalidar el catálogo y descontar stock (RN-051).
        //
        //    Se revalida ahora y no se confía en lo que el carrito traía: entre
        //    que alguien agregó un producto y pulsa «confirmar» pueden pasar
        //    días, y en ese tiempo el producto pudo despublicarse o agotarse.
        for (ItemCarrito linea : carrito.getItems()) {
            Producto producto = linea.getProducto();
            if (!producto.isActivo()) {
                throw new ExcepcionAplicacion(CodigoError.PRODUCT_INACTIVE,
                        "«" + producto.getNombre() + "» ya no está disponible.")
                        .con("productoId", producto.getId())
                        .con("producto", producto.getNombre());
            }
            // Lanza INSUFFICIENT_STOCK con el disponible, que es lo que el
            // checkout necesita para decirle al comprador qué ajustar.
            producto.descontarStock(linea.getCantidad());
        }

        // 2. Recalcular los importes desde cero, en el servidor (RN-051).
        //    La petición no trae ninguno: ver CrearOrdenPeticion.
        PrecioCarrito precio = calculadora.calcular(carrito);

        // 3. Consumir el cupón, solo si de verdad se aplicó (RN-042).
        //
        //    `precio.cuponActivo()` ya contempla vigencia, usos y mínimo. Un cupón
        //    vinculado que hoy no aplica no gasta un uso: el comprador no se
        //    está beneficiando de él.
        Cupon cupon = carrito.getCupon();
        String codigoCupon = null;
        if (cupon != null && precio.cuponActivo()) {
            cupon.registrarUso();
            codigoCupon = cupon.getCodigo();
        }

        // 4. La orden copia; no referencia (RN-052).
        Orden orden = new Orden(
                numeroLibre(),
                idCliente == null ? null : clientes.findById(idCliente).orElse(null),
                peticion.nombreContacto().trim(),
                peticion.email().trim().toLowerCase(),
                vacioComoNulo(peticion.telefono()),
                peticion.direccion().trim(),
                precio.subtotal(),
                precio.descuento(),
                precio.total(),
                codigoCupon);

        for (ItemCarrito linea : carrito.getItems()) {
            orden.agregarLinea(new ItemOrden(linea.getProducto(), linea.getCantidad()));
        }

        // 5. El carrito queda cerrado: ya no se puede tocar (RN-050).
        carrito.marcarConvertido();

        ordenes.save(orden);

        // 6. Ya con el stock descontado, revisar si alguno cayó bajo mínimos o
        //    se agotó. Va al final para que el aviso refleje el stock definitivo
        //    y no un estado intermedio.
        for (ItemCarrito linea : carrito.getItems()) {
            avisos.revisar(linea.getProducto());
        }

        // Volcar antes de mapear: la auditoría la escribe el listener al
        // volcar, así que sin esto la respuesta saldría sin `creadoEn`.
        ordenes.flush();
        return OrdenDetalleRespuesta.de(orden);
    }

    /** La confirmación de la tienda, por número. Nunca por id correlativo (RN-057). */
    @Transactional(readOnly = true)
    public OrdenDetalleRespuesta porNumero(String numero) {
        return ordenes.findConItemsByNumero(numero)
                .map(OrdenDetalleRespuesta::de)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ORDER_NOT_FOUND,
                        "No existe la orden " + numero + "."));
    }

    private Carrito carritoConfirmable(java.util.UUID id) {
        Carrito carrito = carritos.findConDetalleById(id)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.CART_NOT_FOUND,
                        "No existe el carrito " + id + "."));

        if (carrito.getEstado() == EstadoCarrito.CONVERTIDO) {
            // Pasa de verdad: el comprador pulsa dos veces, o vuelve atrás
            // desde la confirmación y reenvía. Merece un código propio para
            // que la tienda lo distinga de un carrito que nunca existió.
            throw new ExcepcionAplicacion(CodigoError.CART_ALREADY_CONVERTED,
                    "Este carrito ya se convirtió en una orden.");
        }
        if (carrito.estaVacio()) {
            throw new ExcepcionAplicacion(CodigoError.CART_EMPTY,
                    "No se puede confirmar un carrito sin productos.");
        }
        return carrito;
    }

    /**
     * Un número que no esté cogido.
     *
     * <p>El sufijo es aleatorio, así que puede repetirse. Con 729 millones de
     * combinaciones por mes la probabilidad es ínfima, pero «ínfima» no es
     * «imposible» y la restricción {@code uq_orden_numero} convertiría la
     * colisión en un 500 delante de alguien que está comprando.
     */
    private String numeroLibre() {
        for (int intento = 0; intento < 5; intento++) {
            String numero = numeros.generar();
            if (!ordenes.existsByNumero(numero)) {
                return numero;
            }
        }
        throw new IllegalStateException(
                "No se pudo generar un número de orden libre en 5 intentos.");
    }

    private static String vacioComoNulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
