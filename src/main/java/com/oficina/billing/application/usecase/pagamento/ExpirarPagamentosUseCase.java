package com.oficina.billing.application.usecase.pagamento;

import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.repository.PagamentoRepository;

import java.time.Duration;
import java.util.UUID;

public class ExpirarPagamentosUseCase {

    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final ProcessadorResultadoPagamento processador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final Duration reserva;
    private final int lote;

    public ExpirarPagamentosUseCase(PagamentoRepository pagamentos,
                                    GatewayPagamento gateway,
                                    ProcessadorResultadoPagamento processador,
                                    UnidadeDeTrabalho unidadeDeTrabalho,
                                    MetricasBilling metricas,
                                    Relogio relogio,
                                    ParametrosReconciliacao parametros) {
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.processador = processador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.metricas = metricas;
        this.relogio = relogio;
        this.reserva = parametros.reserva();
        this.lote = parametros.lote();
    }

    public int executar() {
        var agora = relogio.agora();
        var reservados = unidadeDeTrabalho.executar(
            () -> pagamentos.reservarPendentesVencidos(agora, agora.plus(reserva), lote));
        reservados.forEach(this::encerrar);
        return reservados.size();
    }

    private void encerrar(UUID pagamentoId) {
        try {
            var pendente = pagamentos.buscarPorId(pagamentoId).filter(Pagamento::isPendente);
            if (pendente.isEmpty()) {
                return;
            }
            var desfecho = gateway.buscarPorOrcamento(pendente.get().getOrcamentoId())
                .map(processador::aplicar)
                .orElse(DesfechoPagamento.SEM_PAGAMENTO);
            if (desfecho == DesfechoPagamento.PENDENTE || desfecho == DesfechoPagamento.SEM_PAGAMENTO) {
                processador.expirar(pagamentoId);
            }
        } catch (RuntimeException e) {
            metricas.registrarFalha("expiracao_pagamento", pagamentoId.toString(), e);
        }
    }
}
