package com.retailstore.api.comun.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante de intentos por clave (RN-068).
 *
 * <p>Cuenta los intentos de los últimos {@code ventana} y responde cuánto falta
 * para que se libere uno. Esa cifra es la que va en {@code Retry-After}: sin
 * ella el cliente reintenta de inmediato y empeora justo lo que el límite
 * intenta contener.
 *
 * <p><strong>Es memoria del proceso.</strong> Con varias instancias detrás de
 * un balanceador, el límite efectivo se multiplica por el número de instancias.
 * Para el alcance de este proyecto -una instancia- es correcto; el día que haya
 * dos, esto se sustituye por Redis y el resto del código no se entera porque la
 * interfaz es esta misma.
 */
public class LimitadorIntentos {

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;

    public LimitadorIntentos(int maximo, Duration ventana, Clock reloj) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    /**
     * Registra un intento y dice si se pasó del límite.
     *
     * @return segundos que faltan para poder reintentar, o 0 si aún hay margen
     */
    public long registrar(String clave) {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(ventana);

        Deque<Instant> marcas = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(limite)) {
                marcas.pollFirst();
            }
            if (marcas.size() >= maximo) {
                Instant masAntiguo = marcas.peekFirst();
                long faltan = Duration.between(ahora, masAntiguo.plus(ventana)).toSeconds();
                return Math.max(faltan, 1);
            }
            marcas.addLast(ahora);
            return 0;
        }
    }

    /** Un acceso correcto limpia el contador: el límite castiga fallos, no uso. */
    public void olvidar(String clave) {
        intentos.remove(clave);
    }

    /** Descarta las claves sin intentos vigentes. La invoca una tarea programada. */
    public int purgar() {
        Instant limite = reloj.instant().minus(ventana);
        int antes = intentos.size();
        intentos.entrySet().removeIf(entrada -> {
            Deque<Instant> marcas = entrada.getValue();
            synchronized (marcas) {
                while (!marcas.isEmpty() && marcas.peekFirst().isBefore(limite)) {
                    marcas.pollFirst();
                }
                return marcas.isEmpty();
            }
        });
        return antes - intentos.size();
    }
}
