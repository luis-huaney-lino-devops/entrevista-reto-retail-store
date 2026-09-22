package com.retailstore.api.opinion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.dominio.ProductoDePrueba;
import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que una opinión decide sobre sí misma.
 *
 * <p>La validación de la nota y del texto está aquí y no solo en el DTO: el DTO
 * protege el borde HTTP, la entidad protege cualquier otro camino que llegue a
 * ella (RN-095).
 */
class OpinionTest {

    private static final Producto PRODUCTO = ProductoDePrueba.conId(7L, "199.90");
    private static final Cliente CLIENTE = new Cliente("ana@ejemplo.pe", "Ana Torres", null);

    @Test
    @DisplayName("una nota por encima de 5 no se acepta_RN095")
    void escribir_conNotaPorEncimaDelMaximo_lanzaValidationError_RN095() {
        assertThatThrownBy(() -> new Opinion(PRODUCTO, CLIENTE, (short) 6, "Título", "Cuerpo"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("una nota de cero tampoco: la escala empieza en 1_RN095")
    void escribir_conNotaCero_lanzaValidationError_RN095() {
        assertThatThrownBy(() -> new Opinion(PRODUCTO, CLIENTE, (short) 0, "Título", "Cuerpo"))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @Test
    @DisplayName("una nota sin texto no es una opinión, es un voto_RN095")
    void escribir_conTextoEnBlanco_lanzaValidationError_RN095() {
        assertThatThrownBy(() -> new Opinion(PRODUCTO, CLIENTE, (short) 5, "   ", "Cuerpo"))
                .isInstanceOf(ExcepcionAplicacion.class);
        assertThatThrownBy(() -> new Opinion(PRODUCTO, CLIENTE, (short) 5, "Título", "  "))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @Test
    @DisplayName("el texto se guarda recortado: los espacios del final no son contenido_RN095")
    void escribir_recortaLosEspacios_RN095() {
        Opinion opinion = new Opinion(PRODUCTO, CLIENTE, (short) 4, "  Buen acabado  ", "  Cumple.  ");

        assertThat(opinion.getTitulo()).isEqualTo("Buen acabado");
        assertThat(opinion.getCuerpo()).isEqualTo("Cumple.");
    }

    @Test
    @DisplayName("la insignia no la trae la opinión: nace apagada y la enciende el servidor_RN092")
    void nace_sinCompraVerificada_RN092() {
        Opinion opinion = new Opinion(PRODUCTO, CLIENTE, (short) 5, "Título", "Cuerpo");

        assertThat(opinion.isCompraVerificada()).isFalse();

        opinion.marcarCompraVerificada(true);
        assertThat(opinion.isCompraVerificada()).isTrue();
    }

    @Test
    @DisplayName("una opinión recién escrita no está editada_RN095")
    void fueEditada_recienEscrita_esFalso_RN095() {
        Opinion opinion = new Opinion(PRODUCTO, CLIENTE, (short) 5, "Título", "Cuerpo");

        // Sin volcar todavía no hay fechas de auditoría, y «sin fecha» no puede
        // significar «editada»: la ficha pondría «(editada)» a todo.
        assertThat(opinion.fueEditada()).isFalse();
    }

    @Test
    @DisplayName("dos opiniones nuevas no son la misma aunque las dos tengan id nulo_RN094")
    void esMisma_dosNuevas_noSonLaMisma_RN094() {
        Opinion una = new Opinion(PRODUCTO, CLIENTE, (short) 5, "Título", "Cuerpo");
        Opinion otra = new Opinion(PRODUCTO, CLIENTE, (short) 4, "Otro", "Otro cuerpo");

        // Con Objects.equals sobre los ids -el «arreglo» tentador al
        // NullPointerException- esta prueba fallaría: null es igual a null.
        assertThat(una.esMisma(otra)).isFalse();
        assertThat(una.esMisma(null)).isFalse();
        assertThat(una.esMisma(una)).isTrue();
    }

    @Test
    @DisplayName("una opinión sabe de quién es, y no es de quien no tiene id_RN094")
    void esDe_soloSuAutor_RN094() {
        Opinion opinion = new Opinion(PRODUCTO, CLIENTE, (short) 5, "Título", "Cuerpo");

        // El cliente de la prueba no está persistido: sin id, nadie es su dueño.
        assertThat(opinion.esDe(null)).isFalse();
        assertThat(opinion.esDe(99L)).isFalse();
    }
}
