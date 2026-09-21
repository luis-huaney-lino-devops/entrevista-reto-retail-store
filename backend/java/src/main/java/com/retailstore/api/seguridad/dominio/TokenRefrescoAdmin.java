package com.retailstore.api.seguridad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Un eslabón de la cadena de refresco del panel.
 *
 * <p>Se guarda el <strong>hash</strong> del token, nunca el token: quien lea
 * esta tabla no puede fabricar una sesión. Es el mismo razonamiento que se
 * aplica a las contraseñas y que se olvida a menudo porque «un token es
 * temporal».
 *
 * <p>La {@code familia} agrupa todas las rotaciones de un mismo acceso:
 *
 * <pre>
 *   R1 --usa--&gt; R2 --usa--&gt; R3        normal
 *   R1 --usa--&gt; R2
 *   R1 --usa--&gt; detectado             alguien copió R1: cae la familia entera
 * </pre>
 */
@Entity
@Table(name = "token_refresco_admin")
public class TokenRefrescoAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_refresco_admin")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_administrador", nullable = false)
    private Administrador administrador;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Column(nullable = false)
    private UUID familia;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected TokenRefrescoAdmin() {
        // requerido por JPA
    }

    public TokenRefrescoAdmin(Administrador administrador, String hashToken, UUID familia,
                              Instant expiraEn, Instant creadoEn) {
        this.administrador = administrador;
        this.hashToken = hashToken;
        this.familia = familia;
        this.expiraEn = expiraEn;
        this.creadoEn = creadoEn;
    }

    // ----- reglas de dominio -----

    public boolean estaVigente(Instant ahora) {
        return usadoEn == null && revocadoEn == null && ahora.isBefore(expiraEn);
    }

    public boolean fueUsado() {
        return usadoEn != null;
    }

    public void marcarUsado(Instant momento) {
        this.usadoEn = momento;
    }

    public void revocar(Instant momento) {
        if (revocadoEn == null) {
            this.revocadoEn = momento;
        }
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Administrador getAdministrador() {
        return administrador;
    }

    public String getHashToken() {
        return hashToken;
    }

    public UUID getFamilia() {
        return familia;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
