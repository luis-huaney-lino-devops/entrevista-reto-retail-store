package com.retailstore.api.cuenta.repositorio;

import com.retailstore.api.cuenta.dominio.TipoTokenCliente;
import com.retailstore.api.cuenta.dominio.TokenCliente;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenClienteRepositorio extends JpaRepository<TokenCliente, Long> {

    /**
     * Se busca por el hash, que es lo único que hay guardado. El índice único
     * `uq_token_cliente_hash` hace la búsqueda directa.
     */
    Optional<TokenCliente> findByHashToken(String hashToken);

    /**
     * Invalida los tokens vivos de ese tipo antes de emitir uno nuevo.
     *
     * <p>Sin esto, pedir recuperación tres veces deja tres enlaces válidos a la
     * vez, y el más viejo -que puede estar en un correo reenviado o en una
     * bandeja compartida- sigue sirviendo durante media hora. Solo el último
     * debe funcionar.
     */
    @Modifying
    @Query("""
            update TokenCliente t
               set t.usadoEn = :ahora
             where t.cliente.id = :idCliente
               and t.tipo = :tipo
               and t.usadoEn is null
            """)
    int invalidarVivos(@Param("idCliente") Long idCliente,
                       @Param("tipo") TipoTokenCliente tipo,
                       @Param("ahora") Instant ahora);
}
