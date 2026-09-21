package com.retailstore.api.chat.repositorio;

import com.retailstore.api.chat.dominio.Adjunto;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdjuntoRepositorio extends JpaRepository<Adjunto, Long> {

    /**
     * Adjuntos subidos que nunca llegaron a enviarse.
     *
     * <p>Quien sube un archivo y cierra la pestaña deja una fila sin mensaje.
     * No rompen nada, pero ocupan espacio; los recoge la limpieza programada.
     */
    @Query("select a from Adjunto a where a.mensaje is null and a.creadoEn < :limite")
    List<Adjunto> huerfanosAnterioresA(@Param("limite") Instant limite);
}
