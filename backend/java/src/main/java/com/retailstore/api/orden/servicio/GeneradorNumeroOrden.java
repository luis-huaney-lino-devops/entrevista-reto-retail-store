package com.retailstore.api.orden.servicio;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * El número que ve el comprador: {@code ORD-202609-A7K3M9} (RN-057).
 *
 * <p><strong>Por qué el sufijo es aleatorio y no correlativo.</strong> Con
 * {@code ORD-000123}, cualquiera que compre una vez conoce su número, puede
 * restar y pedir los de otros; y comparando dos compras separadas por un mes
 * sabe exactamente cuánto vende la tienda. La página de confirmación se apoya
 * en que el número no se pueda adivinar, así que esta clase es parte de su
 * seguridad, no una cuestión de formato.
 *
 * <p>El alfabeto excluye {@code 0 O 1 I L}: un número de pedido se dicta por
 * teléfono y se copia a mano, y esas cinco se confunden entre sí. Quedan 30
 * símbolos y 6 posiciones, que son 729 millones de combinaciones por mes.
 */
@Component
public class GeneradorNumeroOrden {

    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LARGO_SUFIJO = 6;
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");

    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public GeneradorNumeroOrden(Clock reloj) {
        this.reloj = reloj;
    }

    public String generar() {
        StringBuilder sufijo = new StringBuilder(LARGO_SUFIJO);
        for (int i = 0; i < LARGO_SUFIJO; i++) {
            sufijo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return "ORD-" + PERIODO.format(reloj.instant().atZone(ZoneOffset.UTC)) + "-" + sufijo;
    }
}
