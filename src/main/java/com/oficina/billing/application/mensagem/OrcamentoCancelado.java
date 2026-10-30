package com.oficina.billing.application.mensagem;

import java.util.UUID;

public record OrcamentoCancelado(UUID orcamentoId, String motivo) implements EventoBilling {}
