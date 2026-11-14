package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.OrcamentoReprovado;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;

import java.util.UUID;

public class ReprovarOrcamentoUseCase {

    private final OrcamentoRepository orcamentos;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final MetricasBilling metricas;
    private final Relogio relogio;

    public ReprovarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                    PublicadorMensagens publicador,
                                    UnidadeDeTrabalho unidadeDeTrabalho,
                                    MetricasBilling metricas,
                                    Relogio relogio) {
        this.orcamentos = orcamentos;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    public Orcamento executar(UUID orcamentoId, Decisor decisor) {
        var agora = relogio.agora();
        var reprovado = unidadeDeTrabalho.executar(() -> {
            var orcamento = orcamentos.buscarPorId(orcamentoId)
                .filter(decisor::podeDecidir)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento", orcamentoId));
            orcamento.reprovar(decisor.canal(), agora);
            var salvo = orcamentos.salvar(orcamento);
            publicador.publicar(Correlacao.de(salvo), new OrcamentoReprovado(salvo.getId(), agora, decisor.canal()));
            return salvo;
        });
        metricas.registrarReprovacao(orcamentoId, decisor.canal());
        return reprovado;
    }
}
