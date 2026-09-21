package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cuenta.dominio.DireccionCliente;
import com.retailstore.api.cuenta.dto.DireccionPeticion;
import com.retailstore.api.cuenta.dto.DireccionRespuesta;
import com.retailstore.api.cuenta.repositorio.DireccionClienteRepositorio;
import com.retailstore.api.ubigeo.dominio.Distrito;
import com.retailstore.api.ubigeo.servicio.ServicioUbigeo;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las direcciones de entrega de un cliente.
 *
 * <p>Dos invariantes gobiernan todo lo de aquí:
 *
 * <ol>
 *   <li><strong>La primera dirección es siempre la predeterminada</strong>,
 *       venga como venga en la petición. Una cuenta con una sola dirección y
 *       ninguna marcada obligaría al checkout a elegir, y elegir por el
 *       usuario cuando solo hay una opción es ruido.</li>
 *   <li><strong>Como mucho una predeterminada.</strong> Lo garantiza el índice
 *       único parcial {@code uq_direccion_cliente_predeterminada}, no este
 *       código.</li>
 * </ol>
 *
 * <p>De ahí el cuidado con el orden de los volcados. Hibernate agrupa las
 * sentencias por tipo de operación, así que marcar la nueva y desmarcar la
 * vieja en el mismo volcado puede ejecutar el {@code UPDATE} que enciende antes
 * del que apaga, y el índice único salta. Se apaga, se vuelca, y solo entonces
 * se enciende. Es el mismo problema que documenta
 * {@code Producto.reemplazarImagenes} con las colecciones.
 */
@Service
@Transactional
public class ServicioDireccion {

    private final DireccionClienteRepositorio direcciones;
    private final ClienteRepositorio clientes;
    private final ServicioUbigeo ubigeo;
    private final Clock reloj;

    public ServicioDireccion(DireccionClienteRepositorio direcciones, ClienteRepositorio clientes,
                             ServicioUbigeo ubigeo, Clock reloj) {
        this.direcciones = direcciones;
        this.clientes = clientes;
        this.ubigeo = ubigeo;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<DireccionRespuesta> listar(Long idCliente) {
        return direcciones.findByClienteIdOrderByPredeterminadaDescIdAsc(idCliente).stream()
                .map(DireccionRespuesta::de)
                .toList();
    }

    public DireccionRespuesta crear(Long idCliente, DireccionPeticion peticion) {
        Cliente cliente = exigirCliente(idCliente);
        Distrito distrito = ubigeo.exigirDistrito(peticion.distritoId());

        DireccionCliente direccion = new DireccionCliente(
                cliente, distrito, peticion.etiqueta().trim(), peticion.destinatario().trim(),
                peticion.telefono().trim(), peticion.calle().trim());
        aplicar(direccion, distrito, peticion);

        // Nace sin marcar y se guarda: es una entidad nueva, así que save().
        // Un flush() a secas no insertaría nada porque nadie la ha metido en la
        // sesión todavía.
        direcciones.save(direccion);

        // La clave es IDENTITY, así que el INSERT ya se ejecutó -Hibernate lo
        // necesita para saber el id- y el recuento incluye la recién creada.
        boolean esLaPrimera = direcciones.countByClienteId(idCliente) == 1;
        if (esLaPrimera || peticion.predeterminada()) {
            hacerPredeterminada(idCliente, direccion);
        }
        return DireccionRespuesta.de(direccion);
    }

    public DireccionRespuesta actualizar(Long idCliente, Long idDireccion, DireccionPeticion peticion) {
        DireccionCliente direccion = exigirDireccion(idCliente, idDireccion);
        Distrito distrito = ubigeo.exigirDistrito(peticion.distritoId());

        direccion.identificar(peticion.etiqueta().trim(), peticion.destinatario().trim(), peticion.telefono().trim());
        aplicar(direccion, distrito, peticion);

        // Solo enciende, nunca apaga: mandar `predeterminada: false` sobre la
        // que ya lo es dejaría al cliente sin ninguna marcada, y no hay forma
        // de saber cuál debería heredarlo. Para cambiarla está
        // POST /{id}/predeterminada, que dice a cuál se mueve.
        if (peticion.predeterminada() && !direccion.isPredeterminada()) {
            hacerPredeterminada(idCliente, direccion);
        }
        // Ya gestionada: flush(), no saveAndFlush(). saveAndFlush() sobre una
        // entidad gestionada hace merge, y devolvería la copia.
        direcciones.flush();
        return DireccionRespuesta.de(direccion);
    }

    /**
     * Eliminación lógica (RN-086).
     *
     * <p>Si la que se va era la predeterminada, hereda la más antigua de las
     * que quedan. Dejar a un cliente con tres direcciones y ninguna marcada
     * obligaría al checkout a preguntar algo que la cuenta ya había respondido.
     */
    public void eliminar(Long idCliente, Long idDireccion) {
        DireccionCliente direccion = exigirDireccion(idCliente, idDireccion);
        boolean eraPredeterminada = direccion.isPredeterminada();

        direccion.quitarPredeterminada();
        direccion.eliminar(reloj.instant(), UsuarioActual.nombre());
        // Se vuelca antes de marcar a la heredera: mientras la eliminada siga
        // encendida en la base, el índice único parcial no deja encender otra.
        direcciones.flush();

        if (eraPredeterminada) {
            direcciones.findByClienteIdOrderByPredeterminadaDescIdAsc(idCliente).stream()
                    .min(Comparator.comparing(DireccionCliente::getId))
                    .ifPresent(heredera -> {
                        heredera.marcarPredeterminada();
                        direcciones.flush();
                    });
        }
    }

    public DireccionRespuesta marcarPredeterminada(Long idCliente, Long idDireccion) {
        DireccionCliente direccion = exigirDireccion(idCliente, idDireccion);
        if (!direccion.isPredeterminada()) {
            hacerPredeterminada(idCliente, direccion);
        }
        return DireccionRespuesta.de(direccion);
    }

    // ------------------------------------------------------------------ apoyo

    /** Apagar, volcar, encender. El orden es lo que respeta el índice único parcial. */
    private void hacerPredeterminada(Long idCliente, DireccionCliente elegida) {
        direcciones.findByClienteIdOrderByPredeterminadaDescIdAsc(idCliente).stream()
                .filter(otra -> !otra.esMisma(elegida))
                .forEach(DireccionCliente::quitarPredeterminada);
        direcciones.flush();

        elegida.marcarPredeterminada();
        direcciones.flush();
    }

    private void aplicar(DireccionCliente direccion, Distrito distrito, DireccionPeticion peticion) {
        direccion.ubicar(
                distrito,
                peticion.calle().trim(),
                vacioComoNulo(peticion.numero()),
                vacioComoNulo(peticion.referencia()),
                vacioComoNulo(peticion.codigoPostal()),
                peticion.latitud(),
                peticion.longitud());
    }

    /**
     * La dirección de otro cliente es un 404, no un 403.
     *
     * <p>Un 403 confirmaría que esa dirección existe, y con ids correlativos
     * eso es un contador de direcciones de la tienda. La consulta va acotada
     * por cliente para que no haya forma de equivocarse.
     */
    private DireccionCliente exigirDireccion(Long idCliente, Long idDireccion) {
        return direcciones.findByIdAndClienteId(idDireccion, idCliente)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ADDRESS_NOT_FOUND,
                        "No encontramos esa dirección."));
    }

    private Cliente exigirCliente(Long idCliente) {
        return clientes.buscarActivo(idCliente)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                        "Tu sesión ya no es válida. Vuelve a entrar."));
    }

    private static String vacioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
