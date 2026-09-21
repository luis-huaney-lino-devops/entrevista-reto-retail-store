package com.retailstore.api.cupon.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cómo se calcula el descuento. */
public enum TipoCupon {

    /** El valor es un porcentaje entre 1 y 100. */
    PORCENTAJE {
        @Override
        public BigDecimal descuentoSobre(BigDecimal subtotal, BigDecimal valor) {
            return subtotal.multiply(valor)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    },

    /** El valor es un importe en soles. */
    MONTO_FIJO {
        @Override
        public BigDecimal descuentoSobre(BigDecimal subtotal, BigDecimal valor) {
            return valor.setScale(2, RoundingMode.HALF_UP);
        }
    };

    /**
     * Importe a descontar, <strong>sin acotar</strong> al subtotal: de eso se
     * encarga el cupón, porque el tope es una regla del cupón y no de la forma
     * de calcularlo.
     */
    public abstract BigDecimal descuentoSobre(BigDecimal subtotal, BigDecimal valor);
}
