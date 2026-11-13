package com.oficina.billing.application.usecase.pagamento;

import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.NotificacaoPagamento;
import com.oficina.billing.domain.repository.NotificacaoPagamentoRepository;

public class ProcessarNotificacaoPagamentoUseCase {

    private final GatewayPagamento gateway;
    private final ProcessadorResultadoPagamento processador;
    private final NotificacaoPagamentoRepository notificacoes;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Relogio relogio;

    public ProcessarNotificacaoPagamentoUseCase(GatewayPagamento gateway,
                                                ProcessadorResultadoPagamento processador,
                                                NotificacaoPagamentoRepository notificacoes,
                                                UnidadeDeTrabalho unidadeDeTrabalho,
                                                Relogio relogio) {
        this.gateway = gateway;
        this.processador = processador;
        this.notificacoes = notificacoes;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public void registrar(String requestId, String dataId, String tipo, boolean assinaturaValida) {
        var notificacao = NotificacaoPagamento.recebida(requestId, dataId, tipo, assinaturaValida, relogio.agora());
        unidadeDeTrabalho.executar(() -> notificacoes.registrar(notificacao));
    }

    public DesfechoPagamento executar(String pagamentoGatewayId) {
        return processador.aplicar(gateway.consultarPagamento(pagamentoGatewayId));
    }
}
