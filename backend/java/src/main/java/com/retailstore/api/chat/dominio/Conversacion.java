package com.retailstore.api.chat.dominio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.comun.auditoria.EntidadAuditable;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.orden.dominio.Orden;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Un hilo de conversación entre un cliente y el equipo.
 *
 * <p>El cliente se identifica con {@code tokenAcceso}, un UUID que no se
 * adivina: es el mismo mecanismo del carrito. Así se puede atender a quien
 * escribe desde la tienda sin exigirle cuenta, y el día que haya sesión de
 * cliente el token deja de ser lo único que identifica.
 */
@Entity
@Table(name = "conversacion")
public class Conversacion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conversacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    /** Opcional: una consulta puede no ser sobre ninguna orden. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_orden")
    private Orden orden;

    @Column(nullable = false, length = 160)
    private String asunto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoConversacion estado = EstadoConversacion.ABIERTA;

    @Column(name = "token_acceso", nullable = false, unique = true)
    private UUID tokenAcceso = UUID.randomUUID();

    @Column(name = "ultimo_mensaje_en")
    private Instant ultimoMensajeEn;

    /**
     * Desnormalizado a propósito: la bandeja ordena por actividad y pinta el
     * contador sin contar los mensajes de cada conversación en cada carga.
     */
    @Column(name = "no_leidos_admin", nullable = false)
    private int noLeidosAdmin;

    /**
     * Lo mismo, en la otra dirección: respuestas del administrador que el
     * cliente todavía no ha visto. Sin esto el cliente solo se entera abriendo
     * el hilo, que es pedirle que lo compruebe cada rato.
     */
    @Column(name = "no_leidos_cliente", nullable = false)
    private int noLeidosCliente;

    @OneToMany(mappedBy = "conversacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("enviadoEn asc")
    private List<Mensaje> mensajes = new ArrayList<>();

    protected Conversacion() {
        // requerido por JPA
    }

    public Conversacion(Cliente cliente, Orden orden, String asunto) {
        this.cliente = cliente;
        this.orden = orden;
        this.asunto = asunto;
    }

    // ----- reglas de dominio -----

    /**
     * Añade un mensaje y actualiza los contadores.
     *
     * <p>Los dos efectos van juntos aquí y no en el servicio porque son
     * inseparables: un mensaje que no actualiza {@code ultimoMensajeEn} deja la
     * bandeja ordenada por un dato viejo.
     */
    public Mensaje agregarMensaje(AutorMensaje autor, String autorNombre, String cuerpo, Instant momento) {
        if (estado == EstadoConversacion.CERRADA) {
            throw new ExcepcionAplicacion(CodigoError.CONVERSATION_CLOSED,
                    "Esta conversación está cerrada. Abre una nueva para seguir.");
        }
        Mensaje mensaje = new Mensaje(this, autor, autorNombre, cuerpo, momento);
        mensajes.add(mensaje);
        this.ultimoMensajeEn = momento;
        if (autor == AutorMensaje.CLIENTE) {
            this.noLeidosAdmin++;
        } else {
            this.noLeidosCliente++;
        }
        return mensaje;
    }

    /** El panel leyó el hilo: se marcan los mensajes del cliente y se pone a cero. */
    public int marcarLeidoPorAdmin(Instant momento) {
        int marcados = 0;
        for (Mensaje mensaje : mensajes) {
            if (mensaje.getAutor() == AutorMensaje.CLIENTE && mensaje.getLeidoEn() == null) {
                mensaje.marcarLeido(momento);
                marcados++;
            }
        }
        this.noLeidosAdmin = 0;
        return marcados;
    }

    /**
     * El cliente leyó el hilo: se marcan los mensajes del administrador y el
     * contador vuelve a cero.
     *
     * <p>Simétrico a {@link #marcarLeidoPorAdmin}: cada lado marca lo que
     * escribió <strong>el otro</strong>. Marcar los propios no significaría
     * nada.
     */
    public int marcarLeidoPorCliente(Instant momento) {
        int marcados = 0;
        for (Mensaje mensaje : mensajes) {
            if (mensaje.getAutor() != AutorMensaje.CLIENTE && mensaje.getLeidoEn() == null) {
                mensaje.marcarLeido(momento);
                marcados++;
            }
        }
        this.noLeidosCliente = 0;
        return marcados;
    }

    public void cerrar() {
        this.estado = EstadoConversacion.CERRADA;
    }

    public void reabrir() {
        this.estado = EstadoConversacion.ABIERTA;
    }

    public boolean estaAbierta() {
        return estado == EstadoConversacion.ABIERTA;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Orden getOrden() {
        return orden;
    }

    public String getAsunto() {
        return asunto;
    }

    public EstadoConversacion getEstado() {
        return estado;
    }

    public UUID getTokenAcceso() {
        return tokenAcceso;
    }

    public Instant getUltimoMensajeEn() {
        return ultimoMensajeEn;
    }

    public int getNoLeidosAdmin() {
        return noLeidosAdmin;
    }

    public int getNoLeidosCliente() {
        return noLeidosCliente;
    }

    public List<Mensaje> getMensajes() {
        return Collections.unmodifiableList(mensajes);
    }
}
