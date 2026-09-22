package com.retailstore.api.carrito.servicio;

import com.retailstore.api.carrito.dominio.Carrito;
import com.retailstore.api.carrito.dominio.ItemCarrito;
import com.retailstore.api.carrito.dto.AgregarItemPeticion;
import com.retailstore.api.carrito.dto.CarritoRespuesta;
import com.retailstore.api.carrito.dto.ItemCarritoRespuesta;
import com.retailstore.api.carrito.repositorio.CarritoRepositorio;
import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.repositorio.ProductoRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cupon.dominio.Cupon;
import com.retailstore.api.cupon.servicio.ServicioCupon;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El carrito vive en el servidor (ADR-0002).
 *
 * <p>Toda mutación devuelve el carrito completo (RN-033): así el cliente nunca
 * recalcula totales por su cuenta y no puede mostrar un importe distinto del que
 * se va a cobrar.
 */
@Service
@Transactional
public class ServicioCarrito {

    private final CarritoRepositorio carritos;
    private final ProductoRepositorio productos;
    private final ServicioCupon cupones;
    private final CalculadoraCarrito calculadora;
    private final Clock reloj;

    public ServicioCarrito(CarritoRepositorio carritos, ProductoRepositorio productos,
                           ServicioCupon cupones, CalculadoraCarrito calculadora, Clock reloj) {
        this.carritos = carritos;
        this.productos = productos;
        this.cupones = cupones;
        this.calculadora = calculadora;
        this.reloj = reloj;
    }

    public CarritoRespuesta crear() {
        return respuesta(carritos.save(new Carrito()));
    }

    @Transactional(readOnly = true)
    public CarritoRespuesta porId(UUID id) {
        return respuesta(buscar(id));
    }

    public CarritoRespuesta agregarItem(UUID idCarrito, AgregarItemPeticion peticion) {
        Carrito carrito = buscar(idCarrito);
        Producto producto = productoVendible(peticion.productoId());

        // RN-030: lo que cuenta es la cantidad ACUMULADA, no la de esta
        // petición. Sin sumar lo que ya había, diez peticiones de uno superan
        // un stock de cinco sin que ninguna lo detecte.
        int yaEnCarrito = carrito.getItems().stream()
                .filter(item -> producto.getId().equals(item.getProducto().getId()))
                .mapToInt(ItemCarrito::getCantidad)
                .findFirst()
                .orElse(0);

        exigirStock(producto, yaEnCarrito + peticion.cantidad());
        carrito.agregarOIncrementar(producto, peticion.cantidad());
        return respuesta(carrito);
    }

    public CarritoRespuesta cambiarCantidad(UUID idCarrito, Long idItem, int cantidad) {
        Carrito carrito = buscar(idCarrito);
        ItemCarrito item = item(carrito, idItem);
        exigirStock(item.getProducto(), cantidad);
        carrito.cambiarCantidad(item, cantidad);
        return respuesta(carrito);
    }

    public CarritoRespuesta eliminarItem(UUID idCarrito, Long idItem) {
        Carrito carrito = buscar(idCarrito);
        if (!carrito.eliminarItem(idItem)) {
            throw itemNoEncontrado(idItem);
        }
        return respuesta(carrito);
    }

    public CarritoRespuesta aplicarCupon(UUID idCarrito, String codigo) {
        Carrito carrito = buscar(idCarrito);
        Cupon cupon = cupones.porCodigo(codigo);

        // Al canjear sí se explica por qué no aplica: el comprador acaba de
        // escribir ese código y necesita saber qué corregir.
        cupon.exigirAplicable(calculadora.calcular(carrito).subtotal(), reloj.instant());
        carrito.aplicarCupon(cupon);
        return respuesta(carrito);
    }

    public CarritoRespuesta quitarCupon(UUID idCarrito) {
        Carrito carrito = buscar(idCarrito);
        carrito.quitarCupon();
        return respuesta(carrito);
    }

    // ------------------------------------------------------------------ apoyo

    private CarritoRespuesta respuesta(Carrito carrito) {
        // Volcar antes de mapear. La línea que se acaba de agregar es una
        // entidad nueva y su identificador lo asigna la base al insertar, no al
        // añadirla a la colección: sin esto la respuesta del POST devolvía
        // `"id": null` y el carrito quedaba inmodificable —no hay con qué
        // llamar a PATCH ni a DELETE— hasta que el cliente volviera a pedirlo.
        // En las lecturas la transacción es de solo lectura y esto no hace nada.
        carritos.flush();

        PrecioCarrito precio = calculadora.calcular(carrito);
        return new CarritoRespuesta(
                carrito.getId(),
                carrito.getItems().stream().map(ItemCarritoRespuesta::de).toList(),
                carrito.totalUnidades(),
                precio.subtotal(),
                precio.descuento(),
                precio.total(),
                carrito.getCupon() == null ? null : carrito.getCupon().getCodigo(),
                precio.cuponActivo(),
                precio.motivoCuponInactivo());
    }

    private Carrito buscar(UUID id) {
        return carritos.findConDetalleById(id)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.CART_NOT_FOUND,
                        "No existe el carrito " + id + "."));
    }

    private static ItemCarrito item(Carrito carrito, Long idItem) {
        return carrito.buscarItem(idItem).orElseThrow(() -> itemNoEncontrado(idItem));
    }

    private static ExcepcionAplicacion itemNoEncontrado(Long idItem) {
        return new ExcepcionAplicacion(CodigoError.CART_ITEM_NOT_FOUND,
                "La línea " + idItem + " no está en este carrito.");
    }

    private Producto productoVendible(Long id) {
        Producto producto = productos.findById(id)
                // find(id) se salta el @SQLRestriction: un producto eliminado
                // seguiría siendo vendible si no se descartara aquí.
                .filter(candidato -> !candidato.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.PRODUCT_NOT_FOUND,
                        "No existe el producto " + id + "."));
        if (!producto.isActivo()) {
            // RN-006: un producto inactivo no se vende, aunque alguien conserve
            // su id de cuando sí estaba publicado.
            throw new ExcepcionAplicacion(CodigoError.PRODUCT_INACTIVE,
                    "El producto «" + producto.getNombre() + "» ya no está disponible.")
                    .con("productoId", producto.getId())
                    .con("productoNombre", producto.getNombre());
        }
        return producto;
    }

    private static void exigirStock(Producto producto, int cantidadTotal) {
        if (cantidadTotal > producto.getStock()) {
            throw new ExcepcionAplicacion(CodigoError.INSUFFICIENT_STOCK,
                    "Solo quedan " + producto.getStock() + " unidades de «" + producto.getNombre() + "».")
                    .con("productoId", producto.getId())
                    .con("solicitado", cantidadTotal)
                    .con("disponible", producto.getStock());
        }
    }
}
