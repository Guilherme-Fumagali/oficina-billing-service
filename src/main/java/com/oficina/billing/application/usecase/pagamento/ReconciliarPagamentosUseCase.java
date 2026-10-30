package com.oficina.billing.application.usecase.pagamento;

import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.repository.PagamentoRepository;

import java.util.Locale;
import java.util.UUID;

public class ReconciliarPagamentosUseCase {

    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final ProcessadorResultadoPagamento processador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final ParametrosReconciliacao parametros;

    public ReconciliarPagamentosUseCase(PagamentoRepository pagamentos,
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
        this.parametros = parametros;
    }

    public int executar() {
        var agora = relogio.agora();
        var reservados = unidadeDeTrabalho.executar(() -> pagamentos.reservarPendentesCriadosAte(
            agora.minus(parametros.idadeMinima()), agora, agora.plus(parametros.reserva()), parametros.lote()));
        reservados.forEach(this::reconciliar);
        return reservados.size();
    }

    private void reconciliar(UUID pagamentoId) {
        try {
            var desfecho = pagamentos.buscarPorId(pagamentoId)
                .flatMap(pagamento -> gateway.buscarPorOrcamento(pagamento.getOrcamentoId()))
                .map(processador::aplicar)
                .orElse(DesfechoPagamento.SEM_PAGAMENTO);
            metricas.registrarReconciliacao(desfecho.name().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            metricas.registrarReconciliacao("erro");
            metricas.registrarFalha("reconciliacao", pagamentoId.toString(), e);
        }
    }
}
