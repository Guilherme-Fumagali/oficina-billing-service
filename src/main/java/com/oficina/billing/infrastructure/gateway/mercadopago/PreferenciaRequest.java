package com.oficina.billing.infrastructure.gateway.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record PreferenciaRequest(
    List<Item> items,
    Pagador payer,
    @JsonProperty("external_reference") String externalReference,
    @JsonProperty("notification_url") String notificationUrl,
    @JsonProperty("back_urls") BackUrls backUrls,
    @JsonProperty("binary_mode") boolean binaryMode,
    boolean expires,
    @JsonProperty("expiration_date_to") String expirationDateTo
) {
    public record Item(
        String id,
        String title,
        int quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("currency_id") String currencyId
    ) {}

    public record Pagador(String email, String name) {}

    public record BackUrls(String success, String failure, String pending) {}
}
