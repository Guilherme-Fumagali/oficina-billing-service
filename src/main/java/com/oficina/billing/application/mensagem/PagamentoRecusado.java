package com.oficina.billing.application.mensagem;

import java.util.UUID;

public record PagamentoRecusado(UUID orcamentoId, UUID pagamentoId, String motivo) implements EventoBilling {}
