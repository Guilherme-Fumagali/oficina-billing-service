package com.oficina.billing.application.usecase.pagamento;

import java.time.Duration;

public record ParametrosReconciliacao(Duration idadeMinima, Duration reserva, int lote) {}
