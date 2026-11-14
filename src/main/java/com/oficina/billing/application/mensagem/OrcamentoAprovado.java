package com.oficina.billing.application.mensagem;

import com.oficina.billing.domain.valueobject.CanalDecisao;

import java.time.Instant;
import java.util.UUID;

public record OrcamentoAprovado(UUID orcamentoId, Instant aprovadoEm, CanalDecisao canal, String linkPagamento) implements EventoBilling {}
