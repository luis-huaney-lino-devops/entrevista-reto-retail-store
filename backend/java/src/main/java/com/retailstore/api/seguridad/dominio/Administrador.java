package com.retailstore.api.seguridad.dominio;

import com.retailstore.api.comun.auditoria.EntidadEliminable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;

/**
 * Quien gestiona la tienda.
 *
 * <p>No comparte tabla con el cliente (ADR-0008). El motivo es concreto: con
 * una sola tabla y un campo {@code rol}, cualquier fallo en el registro público
 * -una asignación de campos sin filtrar, un DTO con un campo de más- es una
 * escalada a administrador. Aquí ese camino no existe porque nada fuera del
 * panel escribe en esta tabla.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios, sin que haya que
// acordarse en cada repositorio. NO se aplica a find(id): ahí Hibernate va
// directo a la clave primaria, y por eso los servicios comprueban
// estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "administrador")
public class Administrador extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_administrador")
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String usuario;

    /** BCrypt con prefijo de algoritmo: {@code {bcrypt}$2a$12$...}. Nunca sale de aquí. */
    @Column(name = "hash_contrasena", nullable = false, length = 120)
    private String hashContrasena;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolAdministrador rol = RolAdministrador.ADMINISTRADOR;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_acceso_en")
    private Instant ultimoAccesoEn;

    protected Administrador() {
        // requerido por JPA
    }

    public Administrador(String usuario, String hashContrasena, String nombre, RolAdministrador rol) {
        this.usuario = usuario;
        this.hashContrasena = hashContrasena;
        this.nombre = nombre;
        this.rol = rol;
    }

    // ----- reglas de dominio -----

    public void registrarAcceso(Instant momento) {
        this.ultimoAccesoEn = momento;
    }

    public void cambiarContrasena(String nuevoHash) {
        this.hashContrasena = nuevoHash;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    public void cambiarRol(RolAdministrador rol) {
        this.rol = rol;
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getHashContrasena() {
        return hashContrasena;
    }

    public String getNombre() {
        return nombre;
    }

    public RolAdministrador getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getUltimoAccesoEn() {
        return ultimoAccesoEn;
    }
}
