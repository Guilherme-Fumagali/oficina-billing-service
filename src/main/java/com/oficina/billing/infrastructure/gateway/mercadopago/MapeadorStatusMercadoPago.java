package com.oficina.billing.infrastructure.gateway.mercadopago;

import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.util.Locale;

public final class MapeadorStatusMercadoPago {

    private MapeadorStatusMercadoPago() {}

    public static StatusPagamento mapear(String status) {
        if (status == null) {
            return StatusPagamento.PENDENTE;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> StatusPagamento.APROVADO;
            case "rejected", "cancelled" -> StatusPagamento.RECUSADO;
            case "refunded", "charged_back" -> StatusPagamento.ESTORNADO;
            default -> StatusPagamento.PENDENTE;
        };
    }
}
