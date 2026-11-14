package com.oficina.billing.infrastructure.gateway.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreferenciaResponse(
    String id,
    @JsonProperty("init_point") String initPoint,
    @JsonProperty("sandbox_init_point") String sandboxInitPoint
) {}
