package com.retailstore.api.orden.web;

import com.retailstore.api.orden.dto.CrearOrdenPeticion;
import com.retailstore.api.orden.dto.OrdenDetalleRespuesta;
import com.retailstore.api.orden.servicio.ServicioCheckout;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El checkout y la confirmación, desde la tienda.
 *
 * <p>Separado de {@code OrdenAdminControlador}: aquel administra órdenes ya
 * creadas y exige sesión de panel; este las crea y <strong>no exige
 * cuenta</strong> (RN-063).
 */
@RestController
@RequestMapping("/api/v1/ordenes")
@Tag(name = "Órdenes (tienda)", description = "Confirmar la compra y consultar el pedido")
public class OrdenTiendaControlador {

    private final ServicioCheckout checkout;

    public OrdenTiendaControlador(ServicioCheckout checkout) {
        this.checkout = checkout;
    }

    /**
     * El cliente autenticado llega por {@code @AuthenticationPrincipal} y es
     * <strong>opcional</strong>: si es nulo, la compra es de invitado y la
     * orden queda sin {@code fk_id_cliente}, con los datos de contacto que
     * vengan en el cuerpo.
     *
     * <p>Lo que <strong>no</strong> puede hacer quien tiene sesión es decidir
     * de quién es la orden: el id sale del token, nunca del cuerpo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmar la compra: convierte un carrito en una orden",
            description = """
                    Revalida el catálogo, descuenta stock y recalcula los importes en el
                    servidor (RN-051). La petición no lleva precios ni totales.

                    Errores esperables: `409 INSUFFICIENT_STOCK` si el stock cambió,
                    `422 PRODUCT_INACTIVE` si un producto se despublicó,
                    `422 CART_EMPTY` y `422 CART_ALREADY_CONVERTED`.
                    """)
    public OrdenDetalleRespuesta confirmar(@Valid @RequestBody CrearOrdenPeticion peticion,
                                           @AuthenticationPrincipal ClienteAutenticado cliente) {
        return checkout.crear(peticion, cliente == null ? null : cliente.id());
    }

    /**
     * La confirmación se busca por número y no por id porque el id es
     * correlativo (RN-057). No pide sesión a propósito: quien compró como
     * invitado no tiene ninguna, y el número aleatorio es lo que hace que esta
     * URL no sea un catálogo de pedidos ajenos.
     */
    @GetMapping("/{numero}")
    @Operation(summary = "Ver un pedido por su número")
    public OrdenDetalleRespuesta ver(@PathVariable String numero) {
        return checkout.porNumero(numero);
    }
}
