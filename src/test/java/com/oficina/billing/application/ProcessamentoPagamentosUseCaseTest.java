package com.oficina.billing.application;

import com.oficina.billing.application.exception.GatewayPagamentoIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PagamentoGateway;
import com.oficina.billing.application.usecase.pagamento.DesfechoPagamento;
import com.oficina.billing.application.usecase.pagamento.ExpirarPagamentosUseCase;
import com.oficina.billing.application.usecase.pagamento.ParametrosReconciliacao;
import com.oficina.billing.application.usecase.pagamento.ProcessadorResultadoPagamento;
import com.oficina.billing.application.usecase.pagamento.ProcessarNotificacaoPagamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.ReconciliarPagamentosUseCase;
import com.oficina.billing.domain.entity.NotificacaoPagamento;
import com.oficina.billing.domain.repository.NotificacaoPagamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.orcamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoPendente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessamentoPagamentosUseCaseTest {

    private static final ParametrosReconciliacao PARAMETROS =
        new ParametrosReconciliacao(Duration.ofSeconds(60), Duration.ofMinutes(2), 20);

    @Mock PagamentoRepository pagamentos;
    @Mock GatewayPagamento gateway;
    @Mock ProcessadorResultadoPagamento processador;
    @Mock NotificacaoPagamentoRepository notificacoes;
    @Mock MetricasBilling metricas;

    @Test
    void notificacaoConsultaOGatewayEAplicaOResultado() {
        var useCase = new ProcessarNotificacaoPagamentoUseCase(gateway, processador, notificacoes,
            new UnidadeDeTrabalhoImediata(), () -> AGORA);
        var resultado = new PagamentoGateway("200", StatusPagamento.APROVADO, "accredited", UUID.randomUUID().toString(),
            "visa", AGORA, "{}");
        when(gateway.consultarPagamento("200")).thenReturn(resultado);
        when(processador.aplicar(resultado)).thenReturn(DesfechoPagamento.APROVADO);

        assertThat(useCase.executar("200")).isEqualTo(DesfechoPagamento.APROVADO);
    }

    @Test
    void recebimentoDaNotificacaoERegistrado() {
        var useCase = new ProcessarNotificacaoPagamentoUseCase(gateway, processador, notificacoes,
            new UnidadeDeTrabalhoImediata(), () -> AGORA);

        useCase.registrar("req-1", "200", "payment", true);

        var notificacao = ArgumentCaptor.forClass(NotificacaoPagamento.class);
        verify(notificacoes).registrar(notificacao.capture());
        assertThat(notificacao.getValue().requestId()).isEqualTo("req-1");
        assertThat(notificacao.getValue().dataId()).isEqualTo("200");
        assertThat(notificacao.getValue().assinaturaValida()).isTrue();
        assertThat(notificacao.getValue().recebidaEm()).isEqualTo(AGORA);
    }

    @Test
    void reconciliacaoReservaPendentesBuscaNoGatewayEAplica() {
        var useCase = new ReconciliarPagamentosUseCase(pagamentos, gateway, processador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA, PARAMETROS);
        var pagamento = pagamentoPendente(orcamentoAprovado());
        var resultado = new PagamentoGateway("201", StatusPagamento.APROVADO, "accredited",
            pagamento.getOrcamentoId().toString(), "visa", AGORA, "{}");
        when(pagamentos.reservarPendentesCriadosAte(AGORA.minusSeconds(60), AGORA, AGORA.plusSeconds(120), 20))
            .thenReturn(List.of(pagamento.getId()));
        when(pagamentos.buscarPorId(pagamento.getId())).thenReturn(Optional.of(pagamento));
        when(gateway.buscarPorOrcamento(pagamento.getOrcamentoId())).thenReturn(Optional.of(resultado));
        when(processador.aplicar(resultado)).thenReturn(DesfechoPagamento.APROVADO);

        assertThat(useCase.executar()).isEqualTo(1);

        verify(processador).aplicar(resultado);
    }

    @Test
    void reconciliacaoSemPagamentoNoGatewayOuComFalhaSegueParaOProximo() {
        var useCase = new ReconciliarPagamentosUseCase(pagamentos, gateway, processador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA, PARAMETROS);
        var semPagamento = pagamentoPendente(orcamentoAprovado());
        var comFalha = pagamentoPendente(orcamentoAprovado());
        when(pagamentos.reservarPendentesCriadosAte(any(), any(), any(), anyInt()))
            .thenReturn(List.of(semPagamento.getId(), comFalha.getId()));
        when(pagamentos.buscarPorId(semPagamento.getId())).thenReturn(Optional.of(semPagamento));
        when(pagamentos.buscarPorId(comFalha.getId())).thenReturn(Optional.of(comFalha));
        when(gateway.buscarPorOrcamento(semPagamento.getOrcamentoId())).thenReturn(Optional.empty());
        var falha = new GatewayPagamentoIndisponivelException("fora", null);
        when(gateway.buscarPorOrcamento(comFalha.getOrcamentoId())).thenThrow(falha);

        assertThat(useCase.executar()).isEqualTo(2);

        verify(processador, never()).aplicar(any());
        verify(metricas).registrarFalha("reconciliacao", comFalha.getId().toString(), falha);
    }

    @Test
    void expiracaoReconciliaUmaUltimaVezEExpiraQuemSeguePendente() {
        var useCase = new ExpirarPagamentosUseCase(pagamentos, gateway, processador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA, PARAMETROS);
        var semResposta = pagamentoPendente(orcamentoAprovado());
        var aindaPendente = pagamentoPendente(orcamentoAprovado());
        var pendenteNoGateway = new PagamentoGateway("202", StatusPagamento.PENDENTE, "pending",
            aindaPendente.getOrcamentoId().toString(), "pix", null, "{}");
        when(pagamentos.reservarPendentesVencidos(AGORA, AGORA.plusSeconds(120), 20))
            .thenReturn(List.of(semResposta.getId(), aindaPendente.getId()));
        when(pagamentos.buscarPorId(semResposta.getId())).thenReturn(Optional.of(semResposta));
        when(pagamentos.buscarPorId(aindaPendente.getId())).thenReturn(Optional.of(aindaPendente));
        when(gateway.buscarPorOrcamento(semResposta.getOrcamentoId())).thenReturn(Optional.empty());
        when(gateway.buscarPorOrcamento(aindaPendente.getOrcamentoId())).thenReturn(Optional.of(pendenteNoGateway));
        when(processador.aplicar(pendenteNoGateway)).thenReturn(DesfechoPagamento.PENDENTE);

        assertThat(useCase.executar()).isEqualTo(2);

        verify(processador).expirar(semResposta.getId());
        verify(processador).expirar(aindaPendente.getId());
    }

    @Test
    void expiracaoNaoExpiraPagamentoAprovadoNaUltimaConsulta() {
        var useCase = new ExpirarPagamentosUseCase(pagamentos, gateway, processador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA, PARAMETROS);
        var pendente = pagamentoPendente(orcamentoAprovado());
        var aprovado = new PagamentoGateway("203", StatusPagamento.APROVADO, "accredited",
            pendente.getOrcamentoId().toString(), "visa", AGORA, "{}");
        when(pagamentos.reservarPendentesVencidos(any(), any(), anyInt())).thenReturn(List.of(pendente.getId()));
        when(pagamentos.buscarPorId(pendente.getId())).thenReturn(Optional.of(pendente));
        when(gateway.buscarPorOrcamento(pendente.getOrcamentoId())).thenReturn(Optional.of(aprovado));
        when(processador.aplicar(aprovado)).thenReturn(DesfechoPagamento.APROVADO);

        useCase.executar();

        verify(processador, never()).expirar(any());
    }

    @Test
    void expiracaoIgnoraPagamentoQueDeixouDeEstarPendenteERegistraFalhas() {
        var useCase = new ExpirarPagamentosUseCase(pagamentos, gateway, processador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA, PARAMETROS);
        var jaAprovado = pagamentoAprovado(orcamentoAprovado(), "204");
        var comFalha = pagamentoPendente(orcamentoAprovado());
        when(pagamentos.reservarPendentesVencidos(any(), any(), anyInt()))
            .thenReturn(List.of(jaAprovado.getId(), comFalha.getId()));
        when(pagamentos.buscarPorId(jaAprovado.getId())).thenReturn(Optional.of(jaAprovado));
        when(pagamentos.buscarPorId(comFalha.getId())).thenReturn(Optional.of(comFalha));
        var falha = new GatewayPagamentoIndisponivelException("fora", null);
        when(gateway.buscarPorOrcamento(comFalha.getOrcamentoId())).thenThrow(falha);

        useCase.executar();

        verify(gateway, never()).buscarPorOrcamento(jaAprovado.getOrcamentoId());
        verify(processador, never()).expirar(any());
        verify(metricas).registrarFalha(eq("expiracao_pagamento"), eq(comFalha.getId().toString()), eq(falha));
    }
}
