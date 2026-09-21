package com.retailstore.api.notificacion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un aviso para el equipo del panel.
 *
 * <p>Es del equipo, no de una persona: leerla la marca leída para todos. Con un
 * equipo pequeño es lo que se espera —lo que ya atendió alguien no tiene que
 * volver a aparecerle a otro—; cuando el equipo crezca se añade una tabla de
 * lecturas por administrador sin tocar a quien las produce.
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeveridadNotificacion severidad;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(length = 400)
    private String detalle;

    /** Ruta del panel a la que lleva. La notificación no sabe de rutas: se la dan. */
    @Column(length = 200)
    private String enlace;

    /**
     * Evita repetir la misma alerta mientras siga sin leerse.
     *
     * <p>«Producto 42 sin stock» es una sola notificación aunque el producto se
     * guarde diez veces. Lo garantiza un índice único parcial sobre las no
     * leídas, no una comprobación previa: dos peticiones a la vez la pasarían
     * las dos.
     */
    @Column(name = "clave_unicidad", length = 120)
    private String claveUnicidad;

    @Column(name = "leida_en")
    private Instant leidaEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Notificacion() {
        // requerido por JPA
    }

    public Notificacion(TipoNotificacion tipo, String titulo, String detalle, String enlace,
                        String claveUnicidad, Instant creadoEn) {
        this.tipo = tipo;
        this.severidad = tipo.severidad();
        this.titulo = titulo;
        this.detalle = detalle;
        this.enlace = enlace;
        this.claveUnicidad = claveUnicidad;
        this.creadoEn = creadoEn;
    }

    public void marcarLeida(Instant momento) {
        if (leidaEn == null) {
            this.leidaEn = momento;
        }
    }

    public boolean estaLeida() {
        return leidaEn != null;
    }

    public Long getId() {
        return id;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public SeveridadNotificacion getSeveridad() {
        return severidad;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getEnlace() {
        return enlace;
    }

    public String getClaveUnicidad() {
        return claveUnicidad;
    }

    public Instant getLeidaEn() {
        return leidaEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
