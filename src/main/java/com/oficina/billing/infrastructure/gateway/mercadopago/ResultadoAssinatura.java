package com.oficina.billing.infrastructure.gateway.mercadopago;

import java.util.Locale;

public enum ResultadoAssinatura {
    VALIDA,
    INVALIDA,
    AUSENTE;

    public String rotulo() {
        return name().toLowerCase(Locale.ROOT);
    }
}
