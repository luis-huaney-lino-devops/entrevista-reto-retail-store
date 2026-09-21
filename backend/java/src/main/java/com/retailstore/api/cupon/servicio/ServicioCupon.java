package com.retailstore.api.cupon.servicio;

import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.cupon.dominio.Cupon;
import com.retailstore.api.cupon.dominio.TipoCupon;
import com.retailstore.api.cupon.dto.CuponPeticion;
import com.retailstore.api.cupon.dto.CuponRespuesta;
import com.retailstore.api.cupon.repositorio.CuponRepositorio;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioCupon {

    private static final int PORCENTAJE_MAXIMO = 100;

    private final CuponRepositorio cupones;
    private final Clock reloj;

    public ServicioCupon(CuponRepositorio cupones, Clock reloj) {
        this.cupones = cupones;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public RespuestaPagina<CuponRespuesta> listar(int pagina, int tamanoPagina) {
        var ahora = reloj.instant();
        return RespuestaPagina.de(
                cupones.findAllByOrderByIdDesc(PageRequest.of(pagina - 1, tamanoPagina)),
                cupon -> CuponRespuesta.de(cupon, ahora));
    }

    @Transactional(readOnly = true)
    public CuponRespuesta porId(Long id) {
        return CuponRespuesta.de(buscar(id), reloj.instant());
    }

    public CuponRespuesta crear(CuponPeticion peticion) {
        String codigo = normalizar(peticion.codigo());
        if (cupones.existsByCodigo(codigo)) {
            throw new ExcepcionAplicacion(CodigoError.DUPLICATE_COUPON_CODE,
                    "Ya existe un cupón con el código '" + codigo + "'.");
        }
        TipoCupon tipo = TipoCupon.valueOf(peticion.tipo());
        validar(tipo, peticion);

        Cupon cupon = new Cupon(
                codigo, tipo, peticion.valor(), minimo(peticion.subtotalMinimo()),
                peticion.iniciaEn(), peticion.terminaEn(), peticion.usosMaximos());
        return CuponRespuesta.de(cupones.save(cupon), reloj.instant());
    }

    public CuponRespuesta actualizar(Long id, CuponPeticion peticion) {
        Cupon cupon = buscar(id);
        TipoCupon tipo = TipoCupon.valueOf(peticion.tipo());
        validar(tipo, peticion);

        cupon.editar(tipo, peticion.valor(), minimo(peticion.subtotalMinimo()),
                peticion.iniciaEn(), peticion.terminaEn(), peticion.usosMaximos(), peticion.activo());
        cupones.flush();
        return CuponRespuesta.de(cupon, reloj.instant());
    }

    /** Búsqueda por código, tal como la escribe el comprador. */
    @Transactional(readOnly = true)
    public Cupon porCodigo(String codigo) {
        return cupones.findByCodigo(normalizar(codigo))
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.COUPON_NOT_FOUND,
                        "El cupón '" + codigo + "' no existe."));
    }

    /**
     * Eliminación lógica (RN-086). El código no se libera: aparece copiado en
     * las órdenes que lo usaron, y reutilizarlo haría que dos cupones distintos
     * compartieran código en el histórico.
     */
    public void eliminar(Long id) {
        Cupon cupon = buscar(id);
        cupon.eliminar(reloj.instant(), UsuarioActual.nombre());
    }

    // ------------------------------------------------------------------ apoyo

    private static void validar(TipoCupon tipo, CuponPeticion peticion) {
        if (!peticion.terminaEn().isAfter(peticion.iniciaEn())) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "La fecha de fin debe ser posterior a la de inicio.");
        }
        if (tipo == TipoCupon.PORCENTAJE
                && peticion.valor().compareTo(BigDecimal.valueOf(PORCENTAJE_MAXIMO)) > 0) {
            // Un porcentaje mayor que 100 regala dinero. La base también lo
            // impide; aquí se atrapa para dar un mensaje que se entienda.
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "Un cupón por porcentaje no puede superar el 100%.");
        }
    }

    /** find(id) se salta el @SQLRestriction: lo eliminado se descarta aquí. */
    private Cupon buscar(Long id) {
        return cupones.findById(id)
                .filter(cupon -> !cupon.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.COUPON_NOT_FOUND,
                        "No existe el cupón " + id + "."));
    }

    private static BigDecimal minimo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /** El comprador escribe en minúsculas y con espacios; el código no distingue. */
    private static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }
}
