package com.oficina.billing.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record MensagemEnvelope<T>(
    UUID mensagemId,
    String tipo,
    Integer versao,
    UUID sagaId,
    UUID osId,
    UUID causadaPor,
    String origem,
    Instant ocorridoEm,
    T dados
) {
    public static final String ORIGEM = "billing-service";
}
