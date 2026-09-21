package com.retailstore.api.comun.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/**
 * Lo que se puede eliminar sin borrarse (RN-086).
 *
 * <p>Está separado de {@link EntidadAuditable} a propósito: no todo es
 * eliminable. Una orden es un hecho ocurrido y un carrito es un estado
 * transitorio; ninguno de los dos tiene sentido «en la papelera», y si
 * heredaran estas columnas alguien acabaría escribiéndolas.
 *
 * <p>Quien la extiende debe llevar además
 * {@code @SQLRestriction("eliminado_en is null")}, que es lo que hace que lo
 * eliminado desaparezca de las consultas sin que haya que acordarse en cada
 * repositorio.
 */
@MappedSuperclass
public abstract class EntidadEliminable extends EntidadAuditable {

    @Column(name = "eliminado_en")
    private Instant eliminadoEn;

    /**
     * Quién lo eliminó.
     *
     * <p>Es la razón de ser de todo esto. Un borrado físico destruye la
     * respuesta a «¿quién quitó este producto y cuándo?», que es exactamente
     * la pregunta que se hace cuando algo desaparece del catálogo sin
     * explicación.
     */
    @Column(name = "eliminado_por", length = 60)
    private String eliminadoPor;

    public boolean estaEliminado() {
        return eliminadoEn != null;
    }

    /** Idempotente: no reescribe quién fue si ya estaba eliminado. */
    public void eliminar(Instant momento, String autor) {
        if (eliminadoEn == null) {
            this.eliminadoEn = momento;
            this.eliminadoPor = autor;
        }
    }

    public void restaurar() {
        this.eliminadoEn = null;
        this.eliminadoPor = null;
    }

    public Instant getEliminadoEn() {
        return eliminadoEn;
    }

    public String getEliminadoPor() {
        return eliminadoPor;
    }
}
