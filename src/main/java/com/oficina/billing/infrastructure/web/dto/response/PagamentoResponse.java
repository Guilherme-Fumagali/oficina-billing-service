package com.oficina.billing.infrastructure.web.dto.response;

import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PagamentoResponse(
    UUID id,
    UUID orcamentoId,
    UUID osId,
    BigDecimal valor,
    StatusPagamento status,
    String linkPagamento,
    String meio,
    String statusDetalhe,
    Instant prazo,
    Instant criadoEm,
    Instant aprovadoEm,
    Instant estornadoEm
) {
    public static PagamentoResponse from(Pagamento pagamento) {
        if (pagamento == null) {
            return null;
        }
        return new PagamentoResponse(pagamento.getId(), pagamento.getOrcamentoId(), pagamento.getOsId(),
            pagamento.getValor(), pagamento.getStatus(), pagamento.getLinkPagamento(), pagamento.getMeio(),
            pagamento.getStatusDetalhe(), pagamento.getPrazo(), pagamento.getCriadoEm(),
            pagamento.getAprovadoEm(), pagamento.getEstornadoEm());
    }
}
