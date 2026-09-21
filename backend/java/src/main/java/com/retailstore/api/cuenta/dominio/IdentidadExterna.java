package com.retailstore.api.cuenta.dominio;

import com.retailstore.api.cliente.dominio.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * La cuenta de Google -u otra- vinculada a un cliente.
 *
 * <p><strong>Lo que identifica es el {@code sujeto}, no el correo.</strong>
 * Google permite cambiar el correo de una cuenta sin que deje de ser la misma
 * persona; si la clave fuera el correo, ese cambio partiría la cuenta en dos, y
 * el correo liberado podría reclamarlo otro.
 */
@Entity
@Table(name = "identidad_externa")
public class IdentidadExterna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_identidad_externa")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProveedorIdentidad proveedor;

    /** El {@code sub} del token del proveedor. Estable e irrepetible. */
    @Column(nullable = false, length = 255)
    private String sujeto;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 160)
    private String nombre;

    @Column(name = "url_foto", length = 500)
    private String urlFoto;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "ultimo_acceso_en")
    private Instant ultimoAccesoEn;

    protected IdentidadExterna() {
        // requerido por JPA
    }

    public IdentidadExterna(Cliente cliente, ProveedorIdentidad proveedor, String sujeto,
                            String email, String nombre, String urlFoto, Instant creadoEn) {
        this.cliente = cliente;
        this.proveedor = proveedor;
        this.sujeto = sujeto;
        this.email = email;
        this.nombre = nombre;
        this.urlFoto = urlFoto;
        this.creadoEn = creadoEn;
        this.ultimoAccesoEn = creadoEn;
    }

    /**
     * Refresca lo que el proveedor pueda haber cambiado desde la última vez.
     *
     * <p>El correo se actualiza aquí y <strong>no</strong> en el cliente: el de
     * la cuenta es su identidad en la tienda (RN-060) y pisarlo con lo que
     * diga Google movería la cuenta de sitio sin que el usuario lo pida.
     */
    public void registrarAcceso(String email, String nombre, String urlFoto, Instant momento) {
        this.email = email;
        if (nombre != null && !nombre.isBlank()) {
            this.nombre = nombre;
        }
        if (urlFoto != null && !urlFoto.isBlank()) {
            this.urlFoto = urlFoto;
        }
        this.ultimoAccesoEn = momento;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public ProveedorIdentidad getProveedor() {
        return proveedor;
    }

    public String getSujeto() {
        return sujeto;
    }

    public String getEmail() {
        return email;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUrlFoto() {
        return urlFoto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUltimoAccesoEn() {
        return ultimoAccesoEn;
    }
}
