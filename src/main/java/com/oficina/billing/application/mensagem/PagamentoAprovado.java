package com.oficina.billing.application.mensagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PagamentoAprovado(UUID orcamentoId, UUID pagamentoId, BigDecimal valor, Instant aprovadoEm, String meio) implements EventoBilling {}
