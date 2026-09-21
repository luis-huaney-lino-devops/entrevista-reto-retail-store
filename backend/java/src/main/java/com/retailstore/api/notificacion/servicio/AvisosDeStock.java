package com.retailstore.api.notificacion.servicio;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.notificacion.dominio.TipoNotificacion;
import org.springframework.stereotype.Component;

/**
 * Traduce el estado del stock de un producto a avisos del panel.
 *
 * <p>Se llama después de cualquier cosa que pueda cambiarlo: guardar un
 * producto, publicarlo, cancelar una orden. El componente decide si hay que
 * avisar, si hay que cambiar el aviso o si hay que retirarlo, y quien lo llama
 * no tiene que saber nada de eso.
 */
@Component
public class AvisosDeStock {

    /** Por debajo de esto da tiempo a reponer; en cero ya se están perdiendo ventas. */
    public static final int UMBRAL_BAJO = 10;

    private final ServicioNotificacion notificaciones;

    public AvisosDeStock(ServicioNotificacion notificaciones) {
        this.notificaciones = notificaciones;
    }

    public void revisar(Producto producto) {
        String claveAgotado = "stock-agotado:" + producto.getId();
        String claveBajo = "stock-bajo:" + producto.getId();

        // Un producto despublicado no pierde ventas por no tener stock: avisar
        // de él sería ruido, y el ruido es lo que hace que se ignore la bandeja.
        if (!producto.isActivo() || producto.estaEliminado()) {
            notificaciones.resolver(claveAgotado);
            notificaciones.resolver(claveBajo);
            return;
        }

        if (producto.getStock() == 0) {
            notificaciones.resolver(claveBajo);
            notificaciones.publicar(
                    TipoNotificacion.STOCK_AGOTADO,
                    "Sin stock: " + producto.getNombre(),
                    "El producto sigue publicado y no se puede comprar.",
                    "/productos/" + producto.getId(),
                    claveAgotado);
            return;
        }

        if (producto.getStock() < UMBRAL_BAJO) {
            notificaciones.resolver(claveAgotado);
            notificaciones.publicar(
                    TipoNotificacion.STOCK_BAJO,
                    "Quedan " + producto.getStock() + " de " + producto.getNombre(),
                    "Conviene reponer antes de que se agote.",
                    "/productos/" + producto.getId(),
                    claveBajo);
            return;
        }

        // Volvió a haber existencias: el aviso deja de tener sentido y se
        // cierra solo. Si hubiera que cerrarlo a mano, la bandeja acabaría
        // llena de alertas de cosas ya resueltas.
        notificaciones.resolver(claveAgotado);
        notificaciones.resolver(claveBajo);
    }
}
