package com.oficina.billing.domain.entity;

import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DadosPagamento(
    UUID id,
    UUID orcamentoId,
    UUID osId,
    BigDecimal valor,
    StatusPagamento status,
    String mpPreferenceId,
    String mpPaymentId,
    String linkPagamento,
    String meio,
    String statusDetalhe,
    Instant prazo,
    Instant criadoEm,
    Instant aprovadoEm,
    Instant estornadoEm,
    String payloadGateway,
    Long versao
) {}
