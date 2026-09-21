package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cuenta.dominio.IdentidadExterna;
import com.retailstore.api.cuenta.dominio.ProveedorIdentidad;
import com.retailstore.api.cuenta.repositorio.IdentidadExternaRepositorio;
import com.retailstore.api.cuenta.servicio.ServicioAccesoTienda.SesionEmitida;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso con Google, una vez el token ya está verificado.
 *
 * <p>Tres caminos, y el segundo es el delicado:
 *
 * <pre>
 *   ya existe esa identidad            -&gt; acceso normal
 *   no existe, pero el correo sí       -&gt; se VINCULA a la cuenta que hay
 *   no existe ni el correo             -&gt; se crea el cliente, sin contraseña
 * </pre>
 *
 * <p><strong>Vincular exige que Google declare el correo verificado</strong>
 * (RN-064). Sin esa comprobación bastaría con crear una cuenta de Google que
 * declare el correo de la víctima para quedarse con su cuenta en la tienda: es
 * un ataque conocido, y esa línea es lo único que lo impide.
 *
 * <p>Aquí se exige también para <em>crear</em>, que es un punto más estricto de
 * lo que dice la tabla de {@code autenticacion.md}. El motivo: una cuenta nueva
 * nace con {@code emailVerificado = true} porque se confía en Google, y crear
 * con un correo que Google no verificó sería marcar como verificado algo que
 * nadie verificó, además de ocupar el correo de otra persona.
 */
@Service
@Transactional
public class ServicioAccesoGoogle {

    private static final Logger log = LoggerFactory.getLogger(ServicioAccesoGoogle.class);

    private final VerificadorTokenGoogle verificador;
    private final IdentidadExternaRepositorio identidades;
    private final ClienteRepositorio clientes;
    private final ServicioAccesoTienda accesos;
    private final Clock reloj;

    public ServicioAccesoGoogle(VerificadorTokenGoogle verificador,
                                IdentidadExternaRepositorio identidades,
                                ClienteRepositorio clientes,
                                ServicioAccesoTienda accesos,
                                Clock reloj) {
        this.verificador = verificador;
        this.identidades = identidades;
        this.clientes = clientes;
        this.accesos = accesos;
        this.reloj = reloj;
    }

    public SesionEmitida acceder(String credencial) {
        IdentidadGoogle identidad = verificador.verificar(credencial);
        Instant ahora = reloj.instant();

        Optional<IdentidadExterna> conocida =
                identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, identidad.sujeto());
        if (conocida.isPresent()) {
            return accederConIdentidadConocida(conocida.get(), identidad, ahora);
        }

        // A partir de aquí el sujeto es nuevo para nosotros, así que lo único
        // que puede enlazarlo con una cuenta existente es el correo -y el
        // correo solo vale si Google lo verificó-.
        if (!identidad.emailVerificado()) {
            throw new ExcepcionAplicacion(CodigoError.EMAIL_NOT_VERIFIED_BY_PROVIDER,
                    "Google no confirma que ese correo sea tuyo. Entra con tu contraseña y vincula la cuenta desde tu perfil.");
        }

        Cliente cliente = clientes.findByEmail(identidad.email())
                .map(existente -> vincular(existente, identidad, ahora))
                .orElseGet(() -> crear(identidad, ahora));

        return accesos.emitirPara(cliente);
    }

    // ------------------------------------------------------------------ apoyo

    private SesionEmitida accederConIdentidadConocida(IdentidadExterna guardada, IdentidadGoogle identidad,
                                                      Instant ahora) {
        Cliente cliente = guardada.getCliente();
        if (!cliente.isActivo() || cliente.estaEliminado()) {
            throw ServicioAccesoTienda.credencialesInvalidas();
        }
        // El correo y el nombre se refrescan en la identidad, no en el cliente:
        // el de la cuenta es su identidad en la tienda (RN-060) y moverlo
        // porque Google lo movió cambiaría de sitio una cuenta sin pedirlo.
        guardada.registrarAcceso(identidad.email(), identidad.nombre(), identidad.urlFoto(), ahora);
        return accesos.emitirPara(cliente);
    }

    private Cliente vincular(Cliente cliente, IdentidadGoogle identidad, Instant ahora) {
        if (!cliente.isActivo() || cliente.estaEliminado()) {
            throw ServicioAccesoTienda.credencialesInvalidas();
        }
        if (identidades.existsByClienteIdAndProveedor(cliente.getId(), ProveedorIdentidad.GOOGLE)) {
            // La cuenta ya tiene otra identidad de Google -otro `sub` con el
            // mismo correo-. Solo pasa si alguien movió un correo entre cuentas
            // de Google, y vincular la segunda dejaría dos llaves para la misma
            // puerta sin que el dueño lo sepa. Mismo 401 de siempre: aquí no se
            // cuenta nada de la cuenta ajena.
            log.warn("Se intentó vincular una segunda identidad de Google al cliente {}", cliente.getId());
            throw ServicioAccesoTienda.credencialesInvalidas();
        }

        identidades.save(nuevaIdentidad(cliente, identidad, ahora));
        // Google acaba de demostrar que el correo es suyo: verificarlo aquí le
        // ahorra al cliente un enlace que ya no prueba nada nuevo.
        cliente.verificarEmail();
        log.info("Identidad de Google vinculada a la cuenta {}", cliente.getId());
        return cliente;
    }

    private Cliente crear(IdentidadGoogle identidad, Instant ahora) {
        String nombre = identidad.nombre() == null || identidad.nombre().isBlank()
                ? identidad.email()
                : identidad.nombre();
        // Entidades nuevas: save(), no flush(). Y el cliente primero, porque la
        // identidad necesita su id.
        Cliente cliente = clientes.save(Cliente.deProveedorExterno(identidad.email(), recortar(nombre, 120)));
        identidades.save(nuevaIdentidad(cliente, identidad, ahora));
        return cliente;
    }

    private static IdentidadExterna nuevaIdentidad(Cliente cliente, IdentidadGoogle identidad, Instant ahora) {
        return new IdentidadExterna(
                cliente,
                ProveedorIdentidad.GOOGLE,
                identidad.sujeto(),
                recortar(identidad.email(), 160),
                recortar(identidad.nombre(), 160),
                recortar(identidad.urlFoto(), 500),
                ahora);
    }

    /**
     * Las columnas tienen largo y lo que manda Google no. Recortar es preferible
     * a que el acceso falle con un error de base de datos que el usuario no
     * puede entender ni arreglar.
     */
    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
