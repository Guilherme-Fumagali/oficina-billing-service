package com.oficina.billing.infrastructure.web.dto.request;

public record NotificacaoMercadoPagoRequest(String action, String type, Dados data) {

    public record Dados(String id) {}
}
