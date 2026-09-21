package com.retailstore.api.comun.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * Auditoría completa: creación, última modificación y versión.
 *
 * <p>La {@code version} no es informativa: es bloqueo optimista. Dos
 * administradores que abren el mismo producto y guardan uno tras otro no se
 * pisan en silencio; el segundo recibe {@code CONCURRENT_MODIFICATION} y
 * recarga. Sin ella, el último en pulsar gana y el primero nunca se entera de
 * que su cambio desapareció.
 */
@MappedSuperclass
public abstract class EntidadAuditable extends EntidadCreada {

    @LastModifiedDate
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @LastModifiedBy
    @Column(name = "actualizado_por", nullable = false, length = 60)
    private String actualizadoPor;

    @Version
    @Column(nullable = false)
    private int version;

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public String getActualizadoPor() {
        return actualizadoPor;
    }

    public int getVersion() {
        return version;
    }
}
