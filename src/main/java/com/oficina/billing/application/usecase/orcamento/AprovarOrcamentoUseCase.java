package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.OrcamentoAprovado;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.SolicitacaoCobranca;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;

import java.time.Duration;
import java.util.UUID;

public class AprovarOrcamentoUseCase {

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final NotificacaoCliente notificacao;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final Duration prazoPagamento;

    public AprovarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                   PagamentoRepository pagamentos,
                                   GatewayPagamento gateway,
                                   PublicadorMensagens publicador,
                                   UnidadeDeTrabalho unidadeDeTrabalho,
                                   NotificacaoCliente notificacao,
                                   MetricasBilling metricas,
                                   Relogio relogio,
                                   Duration prazoPagamento) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.notificacao = notificacao;
        this.metricas = metricas;
        this.relogio = relogio;
        this.prazoPagamento = prazoPagamento;
    }

    public OrcamentoComPagamento executar(UUID orcamentoId, Decisor decisor) {
        var agora = relogio.agora();
        var orcamento = carregar(orcamentoId, decisor);
        orcamento.exigirDecisaoPossivel(agora);

        var prazo = agora.plus(prazoPagamento);
        var cobranca = gateway.criarCobranca(SolicitacaoCobranca.de(orcamento, prazo));

        var aprovado = unidadeDeTrabalho.executar(() -> {
            var atual = carregar(orcamentoId, decisor);
            atual.aprovar(decisor.canal(), agora);
            var salvo = orcamentos.salvar(atual);
            var pagamento = pagamentos.salvar(
                Pagamento.criar(salvo, cobranca.preferenciaId(), cobranca.linkPagamento(), prazo, agora));
            publicador.publicar(Correlacao.de(salvo),
                new OrcamentoAprovado(salvo.getId(), agora, decisor.canal(), pagamento.getLinkPagamento()));
            return new OrcamentoComPagamento(salvo, pagamento);
        });

        metricas.registrarAprovacao(orcamentoId, decisor.canal(), aprovado.orcamento().getTotal());
        enviarLinkPagamento(aprovado);
        return aprovado;
    }

    private Orcamento carregar(UUID orcamentoId, Decisor decisor) {
        return orcamentos.buscarPorId(orcamentoId)
            .filter(decisor::podeDecidir)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento", orcamentoId));
    }

    private void enviarLinkPagamento(OrcamentoComPagamento aprovado) {
        try {
            notificacao.enviarLinkPagamento(aprovado.orcamento(), aprovado.pagamento());
        } catch (RuntimeException e) {
            metricas.registrarFalha("notificacao_pagamento", aprovado.orcamento().getId().toString(), e);
        }
    }
}
