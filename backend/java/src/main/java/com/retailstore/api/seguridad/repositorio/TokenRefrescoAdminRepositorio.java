package com.retailstore.api.seguridad.repositorio;

import com.retailstore.api.seguridad.dominio.TokenRefrescoAdmin;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRefrescoAdminRepositorio extends JpaRepository<TokenRefrescoAdmin, Long> {

    Optional<TokenRefrescoAdmin> findByHashToken(String hashToken);

    /**
     * Revoca la familia entera de una sola pasada. En bucle serían tantas
     * escrituras como rotaciones haya tenido la sesión, y esto ocurre justo
     * cuando ya hay un incidente.
     */
    @Modifying
    @Query("""
            update TokenRefrescoAdmin t
               set t.revocadoEn = :momento
             where t.familia = :familia
               and t.revocadoEn is null""")
    int revocarFamilia(@Param("familia") UUID familia, @Param("momento") Instant momento);

    @Modifying
    @Query("""
            update TokenRefrescoAdmin t
               set t.revocadoEn = :momento
             where t.administrador.id = :idAdministrador
               and t.revocadoEn is null""")
    int revocarTodosDe(@Param("idAdministrador") Long idAdministrador, @Param("momento") Instant momento);

    /** Los tokens caducados no sirven para nada y la tabla crece con cada acceso. */
    @Modifying
    @Query("delete from TokenRefrescoAdmin t where t.expiraEn < :limite")
    int borrarExpiradosAntesDe(@Param("limite") Instant limite);
}
