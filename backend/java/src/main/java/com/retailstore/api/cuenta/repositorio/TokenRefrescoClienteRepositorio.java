package com.retailstore.api.cuenta.repositorio;

import com.retailstore.api.cuenta.dominio.TokenRefrescoCliente;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRefrescoClienteRepositorio extends JpaRepository<TokenRefrescoCliente, Long> {

    Optional<TokenRefrescoCliente> findByHashToken(String hashToken);

    /**
     * Revoca la familia entera de una sola pasada. En bucle serían tantas
     * escrituras como rotaciones haya tenido la sesión, y esto ocurre justo
     * cuando ya hay un incidente.
     */
    @Modifying
    @Query("""
            update TokenRefrescoCliente t
               set t.revocadoEn = :momento
             where t.familia = :familia
               and t.revocadoEn is null""")
    int revocarFamilia(@Param("familia") UUID familia, @Param("momento") Instant momento);

    /** Cierra todas las sesiones del cliente. Lo exige un cambio de contraseña. */
    @Modifying
    @Query("""
            update TokenRefrescoCliente t
               set t.revocadoEn = :momento
             where t.cliente.id = :idCliente
               and t.revocadoEn is null""")
    int revocarTodosDe(@Param("idCliente") Long idCliente, @Param("momento") Instant momento);

    /**
     * Cierra todas las sesiones del cliente <strong>menos una</strong>.
     *
     * <p>Es lo que hace un cambio de contraseña hecho desde la propia cuenta:
     * quien lo pide es porque sospecha de otro, y echarle a él también de donde
     * está sentado no protege de nada y sí hace que el formulario parezca roto.
     */
    @Modifying
    @Query("""
            update TokenRefrescoCliente t
               set t.revocadoEn = :momento
             where t.cliente.id = :idCliente
               and t.revocadoEn is null
               and t.familia <> :familia""")
    int revocarTodosDeSalvo(@Param("idCliente") Long idCliente,
                            @Param("familia") UUID familia,
                            @Param("momento") Instant momento);

    @Modifying
    @Query("delete from TokenRefrescoCliente t where t.expiraEn < :limite")
    int borrarExpiradosAntesDe(@Param("limite") Instant limite);
}
