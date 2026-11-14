package com.oficina.billing.application.mensagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrcamentoGerado(UUID orcamentoId, BigDecimal total, Instant validoAte) implements EventoBilling {}
