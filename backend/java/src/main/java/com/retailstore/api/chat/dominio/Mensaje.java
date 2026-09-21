package com.retailstore.api.chat.dominio;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "mensaje")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conversacion_mensaje")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_conversacion", nullable = false)
    private Conversacion conversacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private AutorMensaje autor;

    /**
     * Copiado, no referenciado.
     *
     * <p>Si el administrador que respondió se elimina, el mensaje sigue
     * diciendo quién contestó. Mismo criterio que las líneas de orden.
     */
    @Column(name = "autor_nombre", nullable = false, length = 120)
    private String autorNombre;

    @Column(columnDefinition = "text")
    private String cuerpo;

    @Column(name = "enviado_en", nullable = false, updatable = false)
    private Instant enviadoEn;

    @Column(name = "leido_en")
    private Instant leidoEn;

    /**
     * Conjunto y no lista a propósito.
     *
     * <p>Hibernate no puede traer dos «bolsas» —dos List sin índice— en la
     * misma consulta: el producto cartesiano haría imposible saber qué fila
     * pertenece a qué colección. Como la conversación ya trae sus mensajes en
     * una lista ordenada, los adjuntos de cada mensaje van en un
     * LinkedHashSet, que conserva el orden de inserción y sí se puede traer a
     * la vez.
     */
    @OneToMany(mappedBy = "mensaje", cascade = CascadeType.ALL)
    private Set<Adjunto> adjuntos = new LinkedHashSet<>();

    protected Mensaje() {
        // requerido por JPA
    }

    Mensaje(Conversacion conversacion, AutorMensaje autor, String autorNombre, String cuerpo, Instant enviadoEn) {
        this.conversacion = conversacion;
        this.autor = autor;
        this.autorNombre = autorNombre;
        this.cuerpo = cuerpo;
        this.enviadoEn = enviadoEn;
    }

    public void adjuntar(Adjunto adjunto) {
        adjunto.asignarA(this);
        adjuntos.add(adjunto);
    }

    public void marcarLeido(Instant momento) {
        if (leidoEn == null) {
            this.leidoEn = momento;
        }
    }

    public Long getId() {
        return id;
    }

    public Conversacion getConversacion() {
        return conversacion;
    }

    public AutorMensaje getAutor() {
        return autor;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public Instant getLeidoEn() {
        return leidoEn;
    }

    public Collection<Adjunto> getAdjuntos() {
        return Collections.unmodifiableSet(adjuntos);
    }
}
