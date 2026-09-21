package com.retailstore.api.cuenta.dominio;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.cliente.dominio.Cliente;
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

/**
 * Un producto en la lista de deseos de un cliente.
 *
 * <p>Del cliente y no del navegador: la gracia de una lista de deseos es que
 * sobreviva al cambio de dispositivo.
 *
 * <p><strong>Sin auditoría y con borrado físico</strong>, al contrario que casi
 * todo lo demás. Quitar algo de favoritos no es un hecho que haya que
 * conservar, y guardarlo en la papelera obligaría a explicar por qué reaparece.
 *
 * <p>Desde Java solo se lee. El alta y la baja pasan por
 * {@code FavoritoRepositorio.agregarSiNoExiste} y {@code quitar} porque la
 * idempotencia que pide el contrato tiene que garantizarla la base: un «mira
 * si existe y si no insértalo» lo atraviesan dos peticiones simultáneas.
 */
@Entity
@Table(name = "favorito")
public class Favorito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_favorito")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_producto", nullable = false)
    private Producto producto;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Favorito() {
        // requerido por JPA
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Producto getProducto() {
        return producto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
