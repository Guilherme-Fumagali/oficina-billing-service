package com.oficina.billing.application;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.PagamentoAprovado;
import com.oficina.billing.application.mensagem.PagamentoRecusado;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PagamentoGateway;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.usecase.pagamento.DesfechoPagamento;
import com.oficina.billing.application.usecase.pagamento.ProcessadorResultadoPagamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.orcamento;
import static com.oficina.billing.support.Fixtures.orcamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoComStatus;
import static com.oficina.billing.support.Fixtures.pagamentoPendente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessadorResultadoPagamentoTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock PagamentoRepository pagamentos;
    @Mock GatewayPagamento gateway;
    @Mock PublicadorMensagens publicador;
    @Mock MetricasBilling metricas;

    ProcessadorResultadoPagamento processador;
    Orcamento orcamento;
    Pagamento pagamento;

    @BeforeEach
    void setUp() {
        processador = novoProcessador(true);
        orcamento = orcamentoAprovado();
        pagamento = pagamentoPendente(orcamento);
        lenient().when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        lenient().when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.of(pagamento));
        lenient().when(pagamentos.buscarPorId(pagamento.getId())).thenReturn(Optional.of(pagamento));
    }

    private ProcessadorResultadoPagamento novoProcessador(boolean recusaEncerra) {
        return new ProcessadorResultadoPagamento(orcamentos, pagamentos, gateway, publicador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA, recusaEncerra);
    }

    private PagamentoGateway resultado(String id, StatusPagamento status, String detalhe) {
        return new PagamentoGateway(id, status, detalhe, orcamento.getId().toString(), "visa",
            status == StatusPagamento.APROVADO ? AGORA.plusSeconds(10) : null, "{\"id\":" + id + "}");
    }

    @Test
    void pagamentoAprovadoPublicaPagamentoAprovado() {
        var desfecho = processador.aplicar(resultado("101", StatusPagamento.APROVADO, "accredited"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.APROVADO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.getMpPaymentId()).isEqualTo("101");
        verify(publicador).publicar(Correlacao.de(orcamento), new PagamentoAprovado(orcamento.getId(),
            pagamento.getId(), pagamento.getValor(), AGORA.plusSeconds(10), "visa"));
        verify(metricas).registrarResultadoPagamento(pagamento.getId(), StatusPagamento.APROVADO);
    }

    @Test
    void aprovacaoSemDataUsaORelogio() {
        var semData = new PagamentoGateway("102", StatusPagamento.APROVADO, "accredited",
            orcamento.getId().toString(), "pix", null, "{}");

        processador.aplicar(semData);

        assertThat(pagamento.getAprovadoEm()).isEqualTo(AGORA);
    }

    @Test
    void notificacaoRepetidaDoMesmoPagamentoEIgnorada() {
        var aprovado = pagamentoAprovado(orcamento, "103");
        when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.of(aprovado));

        var desfecho = processador.aplicar(resultado("103", StatusPagamento.APROVADO, "accredited"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.IGNORADO);
        verifyNoInteractions(publicador, gateway);
    }

    @Test
    void aprovacaoParaOrcamentoCanceladoEEstornadaAutomaticamente() {
        orcamento.cancelar("falha", AGORA);

        var desfecho = processador.aplicar(resultado("104", StatusPagamento.APROVADO, "accredited"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.ESTORNO_AUTOMATICO);
        verify(gateway).estornar("104", "estorno-automatico-104");
        verify(metricas).registrarEstorno("automatico", orcamento.getId(), "104");
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamento.getMpPaymentId()).isEqualTo("104");
        verifyNoInteractions(publicador);
    }

    @Test
    void segundaAprovacaoParaPagamentoJaEncerradoEEstornadaSemAlterarORegistro() {
        var recusado = pagamentoComStatus(orcamento, StatusPagamento.RECUSADO);
        when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.of(recusado));

        var desfecho = processador.aplicar(resultado("105", StatusPagamento.APROVADO, "accredited"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.ESTORNO_AUTOMATICO);
        verify(gateway).estornar("105", "estorno-automatico-105");
        assertThat(recusado.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        verify(pagamentos, never()).salvar(any());
    }

    @Test
    void recusaDefinitivaEncerraOPagamentoEPublicaPagamentoRecusado() {
        var desfecho = processador.aplicar(resultado("106", StatusPagamento.RECUSADO, "cc_rejected_other_reason"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.RECUSADO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        verify(publicador).publicar(Correlacao.de(orcamento),
            new PagamentoRecusado(orcamento.getId(), pagamento.getId(), "cc_rejected_other_reason"));
        verify(metricas).registrarResultadoPagamento(pagamento.getId(), StatusPagamento.RECUSADO);
    }

    @Test
    void recusaSemDetalheUsaMotivoPadrao() {
        processador.aplicar(resultado("107", StatusPagamento.RECUSADO, null));

        verify(publicador).publicar(Correlacao.de(orcamento),
            new PagamentoRecusado(orcamento.getId(), pagamento.getId(), "RECUSADO"));
    }

    @Test
    void recusaParaOrcamentoCanceladoNaoGeraEvento() {
        orcamento.cancelar("compensado", AGORA);

        assertThat(processador.aplicar(resultado("108", StatusPagamento.RECUSADO, "cc_rejected_other_reason")))
            .isEqualTo(DesfechoPagamento.RECUSADO);

        verifyNoInteractions(publicador);
    }

    @Test
    void recusaComPoliticaDeNovaTentativaMantemPendente() {
        var tolerante = novoProcessador(false);

        var desfecho = tolerante.aplicar(resultado("109", StatusPagamento.RECUSADO, "cc_rejected_insufficient_amount"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.PENDENTE);
        assertThat(pagamento.isPendente()).isTrue();
        assertThat(pagamento.getStatusDetalhe()).isEqualTo("cc_rejected_insufficient_amount");
        verifyNoInteractions(publicador);
    }

    @Test
    void recusaParaPagamentoQueNaoEstaPendenteEIgnorada() {
        var aprovado = pagamentoAprovado(orcamento, "110");
        when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.of(aprovado));

        assertThat(processador.aplicar(resultado("111", StatusPagamento.RECUSADO, "x"))).isEqualTo(DesfechoPagamento.IGNORADO);
    }

    @Test
    void estornoFeitoNoPainelDoGatewayEReconhecido() {
        var aprovado = pagamentoAprovado(orcamento, "112");
        when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.of(aprovado));

        var desfecho = processador.aplicar(resultado("112", StatusPagamento.ESTORNADO, "refunded"));

        assertThat(desfecho).isEqualTo(DesfechoPagamento.ESTORNADO);
        assertThat(aprovado.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        verify(metricas).registrarEstorno("externo", orcamento.getId(), "112");
    }

    @Test
    void estornoDeOutroPagamentoEIgnorado() {
        assertThat(processador.aplicar(resultado("113", StatusPagamento.ESTORNADO, "refunded")))
            .isEqualTo(DesfechoPagamento.IGNORADO);
    }

    @Test
    void pagamentoAindaPendenteNaoMudaNada() {
        assertThat(processador.aplicar(resultado("114", StatusPagamento.PENDENTE, "pending_contingency")))
            .isEqualTo(DesfechoPagamento.PENDENTE);
        verify(pagamentos, never()).salvar(any());
    }

    @Test
    void referenciaExternaDesconhecidaEIgnorada() {
        var semReferencia = new PagamentoGateway("115", StatusPagamento.APROVADO, "accredited", "nao-e-uuid", "visa", AGORA, "{}");
        var semPagamento = new PagamentoGateway("116", StatusPagamento.APROVADO, "accredited",
            UUID.randomUUID().toString(), "visa", AGORA, "{}");

        assertThat(processador.aplicar(semReferencia)).isEqualTo(DesfechoPagamento.IGNORADO);
        assertThat(processador.aplicar(semPagamento)).isEqualTo(DesfechoPagamento.IGNORADO);
    }

    @Test
    void expirarPagamentoVencidoPublicaRecusaPorPrazo() {
        var vencido = new ProcessadorResultadoPagamento(orcamentos, pagamentos, gateway, publicador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA.plus(Duration.ofHours(25)), true);

        assertThat(vencido.expirar(pagamento.getId())).isEqualTo(DesfechoPagamento.EXPIRADO);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        verify(publicador).publicar(Correlacao.de(orcamento),
            new PagamentoRecusado(orcamento.getId(), pagamento.getId(), "EXPIRADO"));
        verify(metricas).registrarResultadoPagamento(pagamento.getId(), StatusPagamento.EXPIRADO);
    }

    @Test
    void expirarPagamentoDeOrcamentoCanceladoNaoGeraEvento() {
        orcamento.cancelar("compensado", AGORA);
        var vencido = new ProcessadorResultadoPagamento(orcamentos, pagamentos, gateway, publicador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA.plus(Duration.ofHours(25)), true);

        assertThat(vencido.expirar(pagamento.getId())).isEqualTo(DesfechoPagamento.EXPIRADO);
        verifyNoInteractions(publicador);
    }

    @Test
    void expirarIgnoraPagamentoNoPrazoOuJaEncerrado() {
        assertThat(processador.expirar(pagamento.getId())).isEqualTo(DesfechoPagamento.IGNORADO);
        assertThat(pagamento.isPendente()).isTrue();
        verifyNoInteractions(publicador);
    }

    @Test
    void reutilizaOrcamentoPendenteSemAprovacao() {
        var aguardando = orcamento();
        var pendente = pagamentoPendente(aguardando);
        when(orcamentos.buscarPorId(aguardando.getId())).thenReturn(Optional.of(aguardando));
        when(pagamentos.buscarPorOrcamentoId(aguardando.getId())).thenReturn(Optional.of(pendente));
        var aprovado = new PagamentoGateway("117", StatusPagamento.APROVADO, "accredited",
            aguardando.getId().toString(), "visa", AGORA, "{}");

        assertThat(processador.aplicar(aprovado)).isEqualTo(DesfechoPagamento.ESTORNO_AUTOMATICO);
        verify(gateway).estornar("117", "estorno-automatico-117");
    }
}
