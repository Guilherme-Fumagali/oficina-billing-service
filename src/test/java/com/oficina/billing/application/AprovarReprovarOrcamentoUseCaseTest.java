package com.oficina.billing.application;

import com.oficina.billing.application.exception.GatewayPagamentoIndisponivelException;
import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.OrcamentoAprovado;
import com.oficina.billing.application.mensagem.OrcamentoReprovado;
import com.oficina.billing.application.port.CobrancaCriada;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.SolicitacaoCobranca;
import com.oficina.billing.application.usecase.orcamento.AprovarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.Decisor;
import com.oficina.billing.application.usecase.orcamento.ReprovarOrcamentoUseCase;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.exception.OrcamentoExpiradoException;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.exception.TransicaoInvalidaException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.VALIDADE;
import static com.oficina.billing.support.Fixtures.orcamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AprovarReprovarOrcamentoUseCaseTest {

    private static final Duration PRAZO = Duration.ofHours(24);

    @Mock OrcamentoRepository orcamentos;
    @Mock PagamentoRepository pagamentos;
    @Mock GatewayPagamento gateway;
    @Mock PublicadorMensagens publicador;
    @Mock NotificacaoCliente notificacao;
    @Mock MetricasBilling metricas;

    AprovarOrcamentoUseCase aprovar;
    ReprovarOrcamentoUseCase reprovar;

    @BeforeEach
    void setUp() {
        var unidade = new UnidadeDeTrabalhoImediata();
        aprovar = new AprovarOrcamentoUseCase(orcamentos, pagamentos, gateway, publicador, unidade, notificacao,
            metricas, () -> AGORA, PRAZO);
        reprovar = new ReprovarOrcamentoUseCase(orcamentos, publicador, unidade, metricas, () -> AGORA);
    }

    @Test
    void aprovacaoDoClienteCriaCobrancaPagamentoPendenteEPublicaOLink() {
        var clienteId = UUID.randomUUID();
        var orcamento = orcamento(clienteId);
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(gateway.criarCobranca(any())).thenReturn(new CobrancaCriada("pref-1", "https://mp/checkout/pref-1"));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        var resultado = aprovar.executar(orcamento.getId(), Decisor.cliente(clienteId));

        assertThat(resultado.orcamento().getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(resultado.orcamento().getCanalDecisao()).isEqualTo(CanalDecisao.CLIENTE);
        assertThat(resultado.pagamento().getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(resultado.pagamento().getPrazo()).isEqualTo(AGORA.plus(PRAZO));
        assertThat(resultado.pagamento().getLinkPagamento()).isEqualTo("https://mp/checkout/pref-1");

        var solicitacao = ArgumentCaptor.forClass(SolicitacaoCobranca.class);
        verify(gateway).criarCobranca(solicitacao.capture());
        assertThat(solicitacao.getValue().orcamentoId()).isEqualTo(orcamento.getId());
        assertThat(solicitacao.getValue().itens()).hasSize(2);
        assertThat(solicitacao.getValue().prazo()).isEqualTo(AGORA.plus(PRAZO));

        verify(publicador).publicar(Correlacao.de(orcamento),
            new OrcamentoAprovado(orcamento.getId(), AGORA, CanalDecisao.CLIENTE, "https://mp/checkout/pref-1"));
        verify(notificacao).enviarLinkPagamento(eq(resultado.orcamento()), any(Pagamento.class));
        verify(metricas).registrarAprovacao(orcamento.getId(), CanalDecisao.CLIENTE, orcamento.getTotal());
    }

    @Test
    void funcionarioAprovaNoBalcao() {
        var orcamento = orcamento();
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(gateway.criarCobranca(any())).thenReturn(new CobrancaCriada("pref-2", "https://mp/2"));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        var resultado = aprovar.executar(orcamento.getId(), Decisor.balcao());

        assertThat(resultado.orcamento().getCanalDecisao()).isEqualTo(CanalDecisao.BALCAO);
    }

    @Test
    void clienteDeOutroOrcamentoRecebeNaoEncontrado() {
        var orcamento = orcamento(UUID.randomUUID());
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        var outroCliente = Decisor.cliente(UUID.randomUUID());
        var id = orcamento.getId();

        assertThatThrownBy(() -> aprovar.executar(id, outroCliente)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> reprovar.executar(id, outroCliente)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(gateway, publicador);
    }

    @Test
    void orcamentoJaDecididoOuExpiradoNaoChamaOGateway() {
        var decidido = orcamento();
        decidido.reprovar(CanalDecisao.CLIENTE, AGORA);
        when(orcamentos.buscarPorId(decidido.getId())).thenReturn(Optional.of(decidido));
        var decididoId = decidido.getId();

        assertThatThrownBy(() -> aprovar.executar(decididoId, Decisor.balcao())).isInstanceOf(TransicaoInvalidaException.class);

        var vencido = new AprovarOrcamentoUseCase(orcamentos, pagamentos, gateway, publicador,
            new UnidadeDeTrabalhoImediata(), notificacao, metricas, () -> AGORA.plus(VALIDADE), PRAZO);
        var aguardando = orcamento();
        when(orcamentos.buscarPorId(aguardando.getId())).thenReturn(Optional.of(aguardando));
        var aguardandoId = aguardando.getId();

        assertThatThrownBy(() -> vencido.executar(aguardandoId, Decisor.balcao())).isInstanceOf(OrcamentoExpiradoException.class);
        verifyNoInteractions(gateway, publicador);
    }

    @Test
    void falhaDoGatewayNaoAlteraOOrcamento() {
        var orcamento = orcamento();
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(gateway.criarCobranca(any())).thenThrow(new GatewayPagamentoIndisponivelException("fora", null));
        var id = orcamento.getId();
        var balcao = Decisor.balcao();

        assertThatThrownBy(() -> aprovar.executar(id, balcao)).isInstanceOf(GatewayPagamentoIndisponivelException.class);

        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void falhaNoEmailDoLinkNaoDesfazAAprovacao() {
        var orcamento = orcamento();
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(gateway.criarCobranca(any())).thenReturn(new CobrancaCriada("pref-3", "https://mp/3"));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        var falha = new IllegalStateException("SMTP fora");
        doThrow(falha).when(notificacao).enviarLinkPagamento(any(), any());

        var resultado = aprovar.executar(orcamento.getId(), Decisor.balcao());

        assertThat(resultado.orcamento().getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        verify(metricas).registrarFalha(eq("notificacao_pagamento"), anyString(), eq(falha));
    }

    @Test
    void reprovacaoPublicaOrcamentoReprovado() {
        var clienteId = UUID.randomUUID();
        var orcamento = orcamento(clienteId);
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        Orcamento reprovado = reprovar.executar(orcamento.getId(), Decisor.cliente(clienteId));

        assertThat(reprovado.getStatus()).isEqualTo(StatusOrcamento.REPROVADO);
        verify(publicador).publicar(Correlacao.de(orcamento),
            new OrcamentoReprovado(orcamento.getId(), AGORA, CanalDecisao.CLIENTE));
        verify(metricas).registrarReprovacao(orcamento.getId(), CanalDecisao.CLIENTE);
    }
}
