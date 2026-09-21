package com.retailstore.api.ubigeo.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.ubigeo.dominio.Distrito;
import com.retailstore.api.ubigeo.dto.UbigeoRespuesta;
import com.retailstore.api.ubigeo.repositorio.DepartamentoRepositorio;
import com.retailstore.api.ubigeo.repositorio.DistritoRepositorio;
import com.retailstore.api.ubigeo.repositorio.ProvinciaRepositorio;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los tres desplegables encadenados del formulario de dirección.
 *
 * <p>Se sirve por niveles y no de una vez: el árbol completo son 1 874
 * distritos, unos 90 KB de JSON que casi nadie necesita entero. Quien elige un
 * departamento pide sus provincias, y así.
 */
@Service
@Transactional(readOnly = true)
public class ServicioUbigeo {

    private final DepartamentoRepositorio departamentos;
    private final ProvinciaRepositorio provincias;
    private final DistritoRepositorio distritos;

    public ServicioUbigeo(DepartamentoRepositorio departamentos, ProvinciaRepositorio provincias,
                          DistritoRepositorio distritos) {
        this.departamentos = departamentos;
        this.provincias = provincias;
        this.distritos = distritos;
    }

    public List<UbigeoRespuesta> departamentos() {
        return departamentos.findAllByOrderByNombreAsc().stream().map(UbigeoRespuesta::de).toList();
    }

    /**
     * Provincias de un departamento.
     *
     * <p>Un departamento inexistente devuelve lista vacía y no 404: el id llega
     * de un desplegable que esta misma API acaba de servir, así que un id que
     * no está es un cliente desincronizado, no un recurso que falte.
     */
    public List<UbigeoRespuesta> provinciasDe(Short idDepartamento) {
        return provincias.findByDepartamentoIdOrderByNombreAsc(idDepartamento).stream()
                .map(UbigeoRespuesta::de)
                .toList();
    }

    public List<UbigeoRespuesta> distritosDe(Short idProvincia) {
        return distritos.findByProvinciaIdOrderByNombreAsc(idProvincia).stream()
                .map(UbigeoRespuesta::de)
                .toList();
    }

    /**
     * Distrito por id, con provincia y departamento cargados.
     *
     * <p>Aquí sí hay error: una dirección apunta a un distrito, y guardar una
     * que apunta a un id inventado dejaría un dato que no se puede mostrar.
     */
    public Distrito exigirDistrito(Integer id) {
        return distritos.findConJerarquiaById(id)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.DISTRICT_NOT_FOUND,
                        "El distrito indicado no existe."));
    }
}
