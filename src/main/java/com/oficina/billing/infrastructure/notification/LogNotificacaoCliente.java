package com.oficina.billing.infrastructure.notification;

import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notificacao", name = "canal", havingValue = "log", matchIfMissing = true)
public class LogNotificacaoCliente implements NotificacaoCliente {

    private static final Logger log = LoggerFactory.getLogger(LogNotificacaoCliente.class);

    @Override
    public void enviarOrcamento(Orcamento orcamento, String tokenDecisao) {
        log.info("[NOTIFICAÇÃO] Orçamento {} da OS {} aguardando decisão | Total=R$ {} | Válido até {}",
            orcamento.getId(), orcamento.getOsId(), orcamento.getTotal(), orcamento.getValidoAte());
    }

    @Override
    public void enviarLinkPagamento(Orcamento orcamento, Pagamento pagamento) {
        log.info("[NOTIFICAÇÃO] Pagamento do orçamento {} disponível | Total=R$ {} | Prazo {}",
            orcamento.getId(), pagamento.getValor(), pagamento.getPrazo());
    }
}
