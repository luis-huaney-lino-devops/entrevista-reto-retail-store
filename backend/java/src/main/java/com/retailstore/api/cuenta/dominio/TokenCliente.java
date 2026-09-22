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
 * Un token de un solo uso enviado por correo: verificar la dirección o
 * restablecer la contraseña.
 *
 * <p><strong>Aquí solo vive el hash</strong> (RN-066). El valor original viaja
 * en el enlace del correo y no se guarda en ninguna parte. Quien consiga leer
 * esta tabla —un volcado, una inyección, una copia de seguridad mal guardada—
 * obtiene hashes con los que no puede restablecer nada, igual que pasa con las
 * contraseñas.
 *
 * <p>Se usa SHA-256 y no BCrypt a propósito: el token ya es aleatorio de 256
 * bits, así que no hay nada que un ataque por diccionario pueda adivinar, y un
 * hash rápido permite buscarlo por índice. BCrypt obligaría a recorrer la tabla
 * comparando uno a uno.
 */
@Entity
@Table(name = "token_cliente")
public class TokenCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_cliente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTokenCliente tipo;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected TokenCliente() {
        // requerido por JPA
    }

    public TokenCliente(Cliente cliente, TipoTokenCliente tipo, String hashToken,
                        Instant ahora, java.time.Duration vigencia) {
        this.cliente = cliente;
        this.tipo = tipo;
        this.hashToken = hashToken;
        this.creadoEn = ahora;
        this.expiraEn = ahora.plus(vigencia);
    }

    /**
     * Sirve si no se ha usado y no ha caducado.
     *
     * <p>Las dos condiciones importan por separado: uno caducado que nadie usó
     * y uno usado que aún no caduca son igual de inválidos, y distinguirlos en
     * la respuesta le diría a quien prueba tokens cuál de los dos acertó.
     */
    public boolean esUtilizable(Instant ahora) {
        return usadoEn == null && ahora.isBefore(expiraEn);
    }

    /** Un solo uso: consumirlo lo invalida aunque no haya caducado. */
    public void consumir(Instant ahora) {
        this.usadoEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public TipoTokenCliente getTipo() {
        return tipo;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
