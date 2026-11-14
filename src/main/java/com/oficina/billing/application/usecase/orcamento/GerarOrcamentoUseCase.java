package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.application.comando.ComandoGerarOrcamento;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.comando.TiposComando;
import com.oficina.billing.application.mensagem.ComandoRejeitado;
import com.oficina.billing.application.mensagem.OrcamentoGerado;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.DadosNovoOrcamento;
import com.oficina.billing.domain.entity.ItemOrcamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.valueobject.TokenDecisao;

import java.time.Duration;
import java.util.List;

public class GerarOrcamentoUseCase {

    static final String CODIGO_DADOS_INVALIDOS = "DADOS_INVALIDOS";

    private final OrcamentoRepository orcamentos;
    private final MensagensProcessadas mensagensProcessadas;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final NotificacaoCliente notificacao;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final Duration validade;

    public GerarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                 MensagensProcessadas mensagensProcessadas,
                                 PublicadorMensagens publicador,
                                 UnidadeDeTrabalho unidadeDeTrabalho,
                                 NotificacaoCliente notificacao,
                                 MetricasBilling metricas,
                                 Relogio relogio,
                                 Duration validade) {
        this.orcamentos = orcamentos;
        this.mensagensProcessadas = mensagensProcessadas;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.notificacao = notificacao;
        this.metricas = metricas;
        this.relogio = relogio;
        this.validade = validade;
    }

    public ResultadoComando executar(ComandoGerarOrcamento comando) {
        var geracao = unidadeDeTrabalho.executar(() -> registrar(comando));
        if (geracao.criado() != null) {
            metricas.registrarOrcamentoGerado(geracao.criado().getId());
            enviarOrcamento(geracao.criado(), geracao.token());
        }
        return geracao.resultado();
    }

    private Geracao registrar(ComandoGerarOrcamento comando) {
        if (!mensagensProcessadas.registrar(comando.mensagemId(), TiposComando.GERAR_ORCAMENTO)) {
            return Geracao.semEfeito(ResultadoComando.DUPLICADA);
        }
        var existente = orcamentos.buscarPorOsId(comando.osId());
        if (existente.isPresent()) {
            publicarGerado(comando, existente.get());
            return Geracao.semEfeito(ResultadoComando.APLICADA);
        }
        try {
            var token = TokenDecisao.gerar();
            var orcamento = orcamentos.salvar(Orcamento.criar(dadosDo(comando), token.hash(), relogio.agora(), validade));
            publicarGerado(comando, orcamento);
            return new Geracao(ResultadoComando.APLICADA, orcamento, token.valor());
        } catch (DomainException e) {
            publicador.publicar(comando.correlacao(),
                new ComandoRejeitado(TiposComando.GERAR_ORCAMENTO, e.getMessage(), CODIGO_DADOS_INVALIDOS));
            return Geracao.semEfeito(ResultadoComando.REJEITADA);
        }
    }

    private void publicarGerado(ComandoGerarOrcamento comando, Orcamento orcamento) {
        publicador.publicar(comando.correlacao(),
            new OrcamentoGerado(orcamento.getId(), orcamento.getTotal(), orcamento.getValidoAte()));
    }

    private DadosNovoOrcamento dadosDo(ComandoGerarOrcamento comando) {
        var itens = comando.itens() == null ? List.<ItemOrcamento>of() : comando.itens().stream()
            .map(GerarOrcamentoUseCase::itemDe)
            .toList();
        return new DadosNovoOrcamento(comando.osId(), comando.sagaId(), comando.clienteId(),
            comando.clienteNome(), comando.clienteEmail(), comando.veiculoPlaca(), comando.veiculoModelo(), itens);
    }

    private static ItemOrcamento itemDe(ComandoGerarOrcamento.Item item) {
        if (item == null) {
            throw new DomainException("Item do orçamento ausente.");
        }
        return ItemOrcamento.novo(item.tipo(), item.referenciaId(), item.descricao(), item.quantidade(), item.precoUnitario());
    }

    private void enviarOrcamento(Orcamento orcamento, String token) {
        try {
            notificacao.enviarOrcamento(orcamento, token);
        } catch (RuntimeException e) {
            metricas.registrarFalha("notificacao_orcamento", orcamento.getId().toString(), e);
        }
    }

    private record Geracao(ResultadoComando resultado, Orcamento criado, String token) {
        static Geracao semEfeito(ResultadoComando resultado) {
            return new Geracao(resultado, null, null);
        }
    }
}
