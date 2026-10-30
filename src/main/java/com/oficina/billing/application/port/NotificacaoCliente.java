package com.oficina.billing.application.port;

import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;

public interface NotificacaoCliente {

    void enviarOrcamento(Orcamento orcamento, String tokenDecisao);

    void enviarLinkPagamento(Orcamento orcamento, Pagamento pagamento);
}
