package com.oficina.billing.application.mensagem;

import com.oficina.billing.domain.valueobject.CanalDecisao;

import java.time.Instant;
import java.util.UUID;

public record OrcamentoReprovado(UUID orcamentoId, Instant reprovadoEm, CanalDecisao canal) implements EventoBilling {}
