package com.oficina.billing.domain.entity;

import java.time.Instant;
import java.util.UUID;

public record NotificacaoPagamento(
    UUID id,
    String requestId,
    String dataId,
    String tipo,
    boolean assinaturaValida,
    Instant recebidaEm
) {
    public static NotificacaoPagamento recebida(String requestId, String dataId, String tipo,
                                                boolean assinaturaValida, Instant recebidaEm) {
        return new NotificacaoPagamento(UUID.randomUUID(), requestId, dataId, tipo, assinaturaValida, recebidaEm);
    }
}
