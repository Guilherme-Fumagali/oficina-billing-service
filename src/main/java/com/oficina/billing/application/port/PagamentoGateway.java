package com.oficina.billing.application.port;

import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PagamentoGateway(
    String id,
    StatusPagamento status,
    String statusDetalhe,
    String referenciaExterna,
    String meio,
    Instant aprovadoEm,
    String payload
) {
    public Optional<UUID> orcamentoId() {
        try {
            return Optional.ofNullable(referenciaExterna).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
