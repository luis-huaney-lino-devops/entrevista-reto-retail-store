package com.retailstore.api.opinion.dominio;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.comun.auditoria.EntidadEliminable;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;

/**
 * Lo que una persona opina de un producto que compró -o que conoce- (RN-090 …
 * RN-094).
 *
 * <p>Una por cliente y producto. Quien vuelve a opinar edita la suya: lo
 * garantiza {@code uq_opinion_cliente_producto}, no el servicio.
 *
 * <p>La opinión <strong>no escribe</strong> el promedio del producto. Lo deriva
 * el servicio con una sentencia sobre esta tabla al crear, editar o eliminar
 * (RN-093); si la entidad pudiera tocarlo, el día que alguien añadiera un
 * camino nuevo para escribir opiniones el promedio dejaría de cuadrar en
 * silencio.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios. NO se aplica a
// find(id): ahí Hibernate va directo a la clave primaria, y por eso el servicio
// comprueba estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "opinion")
public class Opinion extends EntidadEliminable {

    public static final short MINIMO = 1;
    public static final short MAXIMO = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_opinion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    /**
     * De 1 a 5, entera. {@code smallint} en la base y {@code short} aquí: con
     * {@code ddl-auto: validate} un {@code int} sobre una columna
     * {@code smallint} impide arrancar la aplicación.
     *
     * <p>Sin medias estrellas a propósito. La media del producto sí lleva
     * decimal porque es un promedio; la valoración de una persona es una
     * elección entre cinco, y ofrecer diez opciones no mejora el dato.
     */
    @Column(nullable = false)
    private short calificacion;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String cuerpo;

    /**
     * RN-092: cierto cuando el autor tiene una orden ENTREGADA con este
     * producto. Lo calcula el servicio al escribir; nunca llega del cliente,
     * que es lo que separa una insignia de un adorno.
     */
    @Column(name = "compra_verificada", nullable = false)
    private boolean compraVerificada;

    protected Opinion() {
        // requerido por JPA
    }

    public Opinion(Producto producto, Cliente cliente, short calificacion, String titulo, String cuerpo) {
        this.producto = producto;
        this.cliente = cliente;
        escribir(calificacion, titulo, cuerpo);
    }

    // ----- reglas de dominio -----

    /**
     * El texto y la nota van juntos porque se envían juntos: no hay forma de
     * cambiar la nota sin repasar lo que se escribió, ni al revés.
     *
     * <p>La validación de rango está también aquí y no solo en el DTO. El
     * {@code @Min}/{@code @Max} protege el borde HTTP; esto protege a la
     * entidad de cualquier otro camino -una importación, una prueba, un
     * servicio nuevo- y hace que el CHECK de la base no sea nunca quien tenga
     * que enterarse.
     */
    public final void escribir(short calificacion, String titulo, String cuerpo) {
        if (calificacion < MINIMO || calificacion > MAXIMO) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "La calificación va de " + MINIMO + " a " + MAXIMO + ".")
                    .con("calificacion", calificacion);
        }
        this.calificacion = calificacion;
        this.titulo = exigirTexto(titulo, "El título no puede estar vacío.");
        this.cuerpo = exigirTexto(cuerpo, "La opinión no puede estar vacía.");
    }

    /** Lo decide el servicio consultando las órdenes; la entidad solo lo guarda. */
    public void marcarCompraVerificada(boolean verificada) {
        this.compraVerificada = verificada;
    }

    /**
     * Si el texto que se lee es el que se escribió, o uno posterior.
     *
     * <p>La auditoría pone {@code actualizadoEn} igual a {@code creadoEn} al
     * insertar, así que la comparación es exacta y no necesita margen.
     */
    public boolean fueEditada() {
        return getActualizadoEn() != null && !getActualizadoEn().equals(getCreadoEn());
    }

    public boolean esDe(Long idCliente) {
        return idCliente != null && cliente != null && idCliente.equals(cliente.getId());
    }

    // ----- identidad -----

    /**
     * Dos opiniones son la misma si comparten identificador persistido. Dos sin
     * id no son iguales aunque las dos lo tengan nulo: son dos objetos nuevos
     * distintos, y {@code Objects.equals} sobre los ids las haría iguales.
     */
    public boolean esMisma(Opinion otra) {
        if (this == otra) {
            return true;
        }
        if (otra == null) {
            return false;
        }
        return id != null && Objects.equals(id, otra.id);
    }

    private static String exigirTexto(String valor, String mensaje) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, mensaje);
        }
        return limpio;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public short getCalificacion() {
        return calificacion;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public boolean isCompraVerificada() {
        return compraVerificada;
    }
}
