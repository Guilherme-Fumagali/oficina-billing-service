package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.OrcamentoExpirado;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.application.usecase.pagamento.ExpirarPagamentosUseCase;
import com.oficina.billing.domain.repository.OrcamentoRepository;

public class ExpirarOrcamentosUseCase {

    private final OrcamentoRepository orcamentos;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final ExpirarPagamentosUseCase expirarPagamentos;
    private final int lote;

    public ExpirarOrcamentosUseCase(OrcamentoRepository orcamentos,
                                    PublicadorMensagens publicador,
                                    UnidadeDeTrabalho unidadeDeTrabalho,
                                    MetricasBilling metricas,
                                    Relogio relogio,
                                    ExpirarPagamentosUseCase expirarPagamentos,
                                    int lote) {
        this.orcamentos = orcamentos;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.metricas = metricas;
        this.relogio = relogio;
        this.expirarPagamentos = expirarPagamentos;
        this.lote = lote;
    }

    public void executar() {
        expirarOrcamentosVencidos();
        expirarPagamentos.executar();
    }

    public int expirarOrcamentosVencidos() {
        return unidadeDeTrabalho.executar(() -> {
            var agora = relogio.agora();
            var vencidos = orcamentos.reivindicarVencidos(agora, lote);
            vencidos.forEach(orcamento -> {
                orcamento.expirar(agora);
                var salvo = orcamentos.salvar(orcamento);
                publicador.publicar(Correlacao.de(salvo), new OrcamentoExpirado(salvo.getId(), agora));
                metricas.registrarOrcamentoExpirado(salvo.getId());
            });
            return vencidos.size();
        });
    }
}
