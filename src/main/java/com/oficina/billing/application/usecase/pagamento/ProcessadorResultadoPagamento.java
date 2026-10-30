package com.oficina.billing.application.usecase.pagamento;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.PagamentoAprovado;
import com.oficina.billing.application.mensagem.PagamentoRecusado;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PagamentoGateway;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.util.UUID;

public class ProcessadorResultadoPagamento {

    static final String MOTIVO_RECUSA_PADRAO = "RECUSADO";

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final boolean recusaEncerra;

    public ProcessadorResultadoPagamento(OrcamentoRepository orcamentos,
                                         PagamentoRepository pagamentos,
                                         GatewayPagamento gateway,
                                         PublicadorMensagens publicador,
                                         UnidadeDeTrabalho unidadeDeTrabalho,
                                         MetricasBilling metricas,
                                         Relogio relogio,
                                         boolean recusaEncerra) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.metricas = metricas;
        this.relogio = relogio;
        this.recusaEncerra = recusaEncerra;
    }

    public DesfechoPagamento aplicar(PagamentoGateway resultado) {
        var orcamentoId = resultado.orcamentoId();
        if (orcamentoId.isEmpty()) {
            return DesfechoPagamento.IGNORADO;
        }
        var desfecho = unidadeDeTrabalho.executar(() -> aplicarNaTransacao(orcamentoId.get(), resultado));
        if (desfecho == DesfechoPagamento.ESTORNO_AUTOMATICO) {
            estornarAutomaticamente(orcamentoId.get(), resultado);
        }
        return desfecho;
    }

    public DesfechoPagamento expirar(UUID pagamentoId) {
        return unidadeDeTrabalho.executar(() -> {
            var agora = relogio.agora();
            var encontrado = pagamentos.buscarPorId(pagamentoId)
                .filter(p -> p.isPendente() && p.vencidoEm(agora));
            if (encontrado.isEmpty()) {
                return DesfechoPagamento.IGNORADO;
            }
            var pagamento = encontrado.get();
            pagamento.expirar(agora);
            var salvo = pagamentos.salvar(pagamento);
            var orcamento = orcamentoDo(salvo);
            if (orcamento.getStatus() == StatusOrcamento.APROVADO) {
                publicador.publicar(Correlacao.de(orcamento),
                    new PagamentoRecusado(orcamento.getId(), salvo.getId(), Pagamento.MOTIVO_EXPIRADO));
            }
            metricas.registrarResultadoPagamento(salvo.getId(), StatusPagamento.EXPIRADO);
            return DesfechoPagamento.EXPIRADO;
        });
    }

    private DesfechoPagamento aplicarNaTransacao(UUID orcamentoId, PagamentoGateway resultado) {
        var encontrado = pagamentos.buscarPorOrcamentoId(orcamentoId);
        if (encontrado.isEmpty()) {
            return DesfechoPagamento.IGNORADO;
        }
        var pagamento = encontrado.get();
        return switch (resultado.status()) {
            case APROVADO -> aplicarAprovacao(pagamento, resultado);
            case RECUSADO -> aplicarRecusa(pagamento, resultado);
            case ESTORNADO -> aplicarEstornoExterno(pagamento, resultado);
            case PENDENTE, EXPIRADO -> DesfechoPagamento.PENDENTE;
        };
    }

    private DesfechoPagamento aplicarAprovacao(Pagamento pagamento, PagamentoGateway resultado) {
        if (pagamento.isAprovado() && pagamento.correspondeAo(resultado.id())) {
            return DesfechoPagamento.IGNORADO;
        }
        var orcamento = orcamentoDo(pagamento);
        if (!pagamento.isPendente() || orcamento.getStatus() != StatusOrcamento.APROVADO) {
            return DesfechoPagamento.ESTORNO_AUTOMATICO;
        }
        pagamento.aprovar(resultado.id(), resultado.meio(), resultado.statusDetalhe(),
            resultado.aprovadoEm() != null ? resultado.aprovadoEm() : relogio.agora(), resultado.payload());
        var salvo = pagamentos.salvar(pagamento);
        publicador.publicar(Correlacao.de(orcamento), new PagamentoAprovado(
            orcamento.getId(), salvo.getId(), salvo.getValor(), salvo.getAprovadoEm(), salvo.getMeio()));
        metricas.registrarResultadoPagamento(salvo.getId(), StatusPagamento.APROVADO);
        return DesfechoPagamento.APROVADO;
    }

    private DesfechoPagamento aplicarRecusa(Pagamento pagamento, PagamentoGateway resultado) {
        if (!pagamento.isPendente()) {
            return DesfechoPagamento.IGNORADO;
        }
        if (!recusaEncerra) {
            pagamento.registrarTentativaRecusada(resultado.statusDetalhe(), resultado.payload());
            pagamentos.salvar(pagamento);
            return DesfechoPagamento.PENDENTE;
        }
        pagamento.recusar(resultado.id(), resultado.statusDetalhe(), resultado.payload());
        var salvo = pagamentos.salvar(pagamento);
        var orcamento = orcamentoDo(salvo);
        if (orcamento.getStatus() == StatusOrcamento.APROVADO) {
            var motivo = resultado.statusDetalhe() != null ? resultado.statusDetalhe() : MOTIVO_RECUSA_PADRAO;
            publicador.publicar(Correlacao.de(orcamento), new PagamentoRecusado(orcamento.getId(), salvo.getId(), motivo));
        }
        metricas.registrarResultadoPagamento(salvo.getId(), StatusPagamento.RECUSADO);
        return DesfechoPagamento.RECUSADO;
    }

    private DesfechoPagamento aplicarEstornoExterno(Pagamento pagamento, PagamentoGateway resultado) {
        if (!pagamento.isAprovado() || !pagamento.correspondeAo(resultado.id())) {
            return DesfechoPagamento.IGNORADO;
        }
        pagamento.estornar(relogio.agora());
        pagamentos.salvar(pagamento);
        metricas.registrarEstorno("externo", pagamento.getOrcamentoId(), resultado.id());
        return DesfechoPagamento.ESTORNADO;
    }

    private void estornarAutomaticamente(UUID orcamentoId, PagamentoGateway resultado) {
        gateway.estornar(resultado.id(), "estorno-automatico-" + resultado.id());
        metricas.registrarEstorno("automatico", orcamentoId, resultado.id());
        unidadeDeTrabalho.executar(() -> pagamentos.buscarPorOrcamentoId(orcamentoId)
            .filter(Pagamento::isPendente)
            .ifPresent(pagamento -> {
                var agora = relogio.agora();
                pagamento.aprovar(resultado.id(), resultado.meio(), resultado.statusDetalhe(),
                    resultado.aprovadoEm() != null ? resultado.aprovadoEm() : agora, resultado.payload());
                pagamento.estornar(agora);
                pagamentos.salvar(pagamento);
            }));
    }

    private Orcamento orcamentoDo(Pagamento pagamento) {
        return orcamentos.buscarPorId(pagamento.getOrcamentoId())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento", pagamento.getOrcamentoId()));
    }
}
