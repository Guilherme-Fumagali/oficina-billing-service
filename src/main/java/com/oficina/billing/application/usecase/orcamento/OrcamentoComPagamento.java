package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;

public record OrcamentoComPagamento(Orcamento orcamento, Pagamento pagamento) {}
