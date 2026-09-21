package com.retailstore.api.cliente.dominio;

import com.retailstore.api.comun.auditoria.EntidadEliminable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import java.util.Locale;

/**
 * Persona con cuenta en la tienda.
 *
 * <p>No comparte tabla con el administrador (ADR-0008): el registro público
 * escribe aquí, y esta tabla no da acceso al panel bajo ninguna circunstancia.
 *
 * <p>El registro, la verificación de correo y el acceso con Google llegan con
 * el bloque de identidad. Lo que existe hoy es lo que el panel necesita: saber
 * quién compró y poder hablar con esa persona.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios. NO se aplica a
// find(id): ahí Hibernate va directo a la clave primaria.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "cliente")
public class Cliente extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long id;

    /** En minúsculas. Es la identidad del cliente. */
    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado;

    /**
     * Nulo si se registró con Google.
     *
     * <p>Es un estado legítimo y hay que tratarlo: a quien entró con Google no
     * se le puede pedir «la contraseña actual» para cambiarla.
     */
    @Column(name = "hash_contrasena", length = 120)
    private String hashContrasena;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_acceso_en")
    private Instant ultimoAccesoEn;

    protected Cliente() {
        // requerido por JPA
    }

    public Cliente(String email, String nombre, String telefono) {
        this.email = normalizar(email);
        this.nombre = nombre;
        this.telefono = telefono;
    }

    // ----- reglas de dominio -----

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    public boolean tieneContrasena() {
        return hashContrasena != null;
    }

    /** El correo se guarda siempre en minúsculas: si no, sería dos cuentas. */
    public static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getUltimoAccesoEn() {
        return ultimoAccesoEn;
    }
}
