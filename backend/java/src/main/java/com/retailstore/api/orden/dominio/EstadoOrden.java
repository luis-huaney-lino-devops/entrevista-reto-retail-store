package com.retailstore.api.orden.dominio;

import java.util.Set;

/**
 * Ciclo de vida de una orden (RN-054).
 *
 * <pre>
 *   PENDIENTE ──▶ PAGADA ──▶ ENVIADA ──▶ ENTREGADA
 *       │            │
 *       └────────────┴──▶ CANCELADA
 * </pre>
 *
 * <p>Las transiciones válidas se declaran aquí y no en el servicio a propósito:
 * un {@code if} en el servicio se olvida en el segundo sitio que cambie estados,
 * y entonces existen dos versiones de la regla.
 */
public enum EstadoOrden {

    PENDIENTE("Pendiente"),
    PAGADA("Pagada"),
    ENVIADA("Enviada"),
    ENTREGADA("Entregada"),
    CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoOrden(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** A dónde puede ir desde aquí. Vacío significa que es terminal. */
    public Set<EstadoOrden> siguientes() {
        return switch (this) {
            case PENDIENTE -> Set.of(PAGADA, CANCELADA);
            case PAGADA -> Set.of(ENVIADA, CANCELADA);
            case ENVIADA -> Set.of(ENTREGADA);
            // Una orden entregada es terminal, y una cancelada no revive: ambas
            // describen un hecho ocurrido.
            case ENTREGADA, CANCELADA -> Set.of();
        };
    }

    public boolean puedeIrA(EstadoOrden destino) {
        return siguientes().contains(destino);
    }

    /** Cancelar devuelve el stock (RN-055); las demás transiciones no lo tocan. */
    public boolean devuelveStock(EstadoOrden destino) {
        return destino == CANCELADA;
    }

    public boolean cuentaComoVenta() {
        return this != CANCELADA;
    }
}
