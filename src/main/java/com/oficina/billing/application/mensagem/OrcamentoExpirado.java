package com.oficina.billing.application.mensagem;

import java.time.Instant;
import java.util.UUID;

public record OrcamentoExpirado(UUID orcamentoId, Instant expiradoEm) implements EventoBilling {}
