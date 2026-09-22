package com.retailstore.api.seguridad.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.retailstore.api.seguridad.servicio.ServicioJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * En qué rutas mira el filtro de la tienda la cabecera {@code Authorization}.
 *
 * <p>Esta prueba existe por un fallo real y caro de encontrar. El filtro decide
 * por <strong>prefijo</strong> de ruta, y al añadir {@code /api/v1/ordenes} nadie
 * lo incluyó. El resultado no fue un error visible sino dos consecuencias
 * calladas: el historial respondía {@code 401} a quien sí tenía sesión, y el
 * checkout recibía el principal nulo y registraba como <strong>compra de
 * invitado</strong> la de un cliente autenticado —que luego no aparecía en «Mis
 * pedidos» ni permitía abrir el chat—.
 *
 * <p>Un prefijo que falte aquí no rompe nada que se note al compilar ni al
 * arrancar. Por eso se fija con una prueba.
 */
class FiltroJwtTiendaTest {

    private final FiltroJwtTienda filtro = new FiltroJwtTienda(
            mock(ServicioJwt.class), mock(HandlerExceptionResolver.class));

    private boolean seSalta(String uri) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", uri);
        peticion.setRequestURI(uri);
        return filtro.shouldNotFilter(peticion);
    }

    @Test
    @DisplayName("mira las rutas de cuenta que exigen sesión")
    void miraLasRutasDeCuenta() {
        assertThat(seSalta("/api/v1/cuenta/yo")).isFalse();
        assertThat(seSalta("/api/v1/cuenta/direcciones")).isFalse();
        assertThat(seSalta("/api/v1/cuenta/favoritos")).isFalse();
    }

    @Test
    @DisplayName("mira las órdenes de la tienda: son del mundo del cliente")
    void miraLasOrdenesDeLaTienda() {
        // El historial. Sin esto responde 401 a quien sí tiene sesión.
        assertThat(seSalta("/api/v1/ordenes/mios")).isFalse();
        // El checkout. Sin esto el principal llega nulo y la compra de alguien
        // con sesión se guarda como de invitado, en silencio.
        assertThat(seSalta("/api/v1/ordenes")).isFalse();
    }

    @Test
    @DisplayName("NO mira las rutas que emiten la sesión: son las que la crean")
    void noMiraLasRutasDeSesion() {
        assertThat(seSalta("/api/v1/cuenta/acceso")).isTrue();
        assertThat(seSalta("/api/v1/cuenta/registro")).isTrue();
        assertThat(seSalta("/api/v1/cuenta/refrescar")).isTrue();
    }

    @Test
    @DisplayName("NO mira las del panel: su token es de otra audiencia")
    void noMiraLasDelPanel() {
        // Si las mirara, rechazaría por audiencia el token legítimo del panel
        // antes de que su propio filtro pudiera validarlo.
        assertThat(seSalta("/api/v1/admin/productos")).isTrue();
        assertThat(seSalta("/api/v1/admin/ordenes")).isTrue();
    }

    @Test
    @DisplayName("NO mira el catálogo público: ahí no hay sesión que leer")
    void noMiraElCatalogoPublico() {
        assertThat(seSalta("/api/v1/productos")).isTrue();
        assertThat(seSalta("/api/v1/categorias")).isTrue();
        assertThat(seSalta("/health")).isTrue();
    }
}
