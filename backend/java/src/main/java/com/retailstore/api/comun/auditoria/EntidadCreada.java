package com.retailstore.api.comun.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Quién creó una fila y cuándo. Para entidades <strong>inmutables</strong>, que
 * no tienen actualización ni versión: un archivo no se edita, se reemplaza.
 *
 * <p>Las fechas las pone {@code ProveedorFechaHora}, que lee el {@link java.time.Clock}
 * inyectado. Por eso una prueba puede fijar el instante, cosa que un
 * {@code Instant.now()} dentro de un {@code @PrePersist} no permite.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadCreada {

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @CreatedBy
    @Column(name = "creado_por", nullable = false, updatable = false, length = 60)
    private String creadoPor;

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public String getCreadoPor() {
        return creadoPor;
    }
}
