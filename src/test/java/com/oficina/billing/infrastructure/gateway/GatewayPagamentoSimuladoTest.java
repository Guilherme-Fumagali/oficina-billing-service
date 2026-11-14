package com.oficina.billing.infrastructure.gateway;

import com.oficina.billing.application.exception.GatewayPagamentoException;
import com.oficina.billing.application.port.SolicitacaoCobranca;
import com.oficina.billing.application.usecase.pagamento.DesfechoPagamento;
import com.oficina.billing.application.usecase.pagamento.ProcessarNotificacaoPagamentoUseCase;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.infrastructure.gateway.simulado.GatewayPagamentoSimulado;
import com.oficina.billing.infrastructure.web.controller.SimulacaoPagamentoController;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayPagamentoSimuladoTest {

    private final GatewayPagamentoSimulado simulador = new GatewayPagamentoSimulado(() -> AGORA, "http://localhost:8081");

    @Test
    void cobrancaApontaParaOEndpointDeSimulacao() {
        var orcamentoId = UUID.randomUUID();

        var cobranca = simulador.criarCobranca(new SolicitacaoCobranca(orcamentoId, UUID.randomUUID(), "Maria",
            "maria@example.com", List.of(), AGORA.plusSeconds(3600)));

        assertThat(cobranca.linkPagamento()).isEqualTo("http://localhost:8081/api/pagamentos/simulacao/" + orcamentoId);
        assertThat(cobranca.preferenciaId()).isEqualTo("simulada-" + orcamentoId);
    }

    @Test
    void simulacaoGuardaOResultadoParaConsultaEBusca() {
        var orcamentoId = UUID.randomUUID();
        var recusado = simulador.simular(orcamentoId, false);
        var aprovado = simulador.simular(orcamentoId, true);
        var outro = simulador.simular(UUID.randomUUID(), false);

        assertThat(simulador.consultarPagamento(recusado).status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(simulador.consultarPagamento(aprovado).aprovadoEm()).isEqualTo(AGORA);
        assertThat(simulador.buscarPorOrcamento(orcamentoId)).get().extracting(p -> p.id()).isEqualTo(aprovado);
        assertThat(simulador.buscarPorOrcamento(UUID.fromString(simulador.consultarPagamento(outro).referenciaExterna())))
            .get().extracting(p -> p.status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(simulador.buscarPorOrcamento(UUID.randomUUID())).isEmpty();
    }

    @Test
    void estornoMarcaOPagamentoSimuladoComoEstornado() {
        var id = simulador.simular(UUID.randomUUID(), true);

        simulador.estornar(id, "chave");
        simulador.estornar("inexistente", "chave");

        assertThat(simulador.consultarPagamento(id).status()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThatThrownBy(() -> simulador.consultarPagamento("inexistente")).isInstanceOf(GatewayPagamentoException.class);
    }

    @Test
    void endpointDeSimulacaoMostraOpcoesEProcessaOResultadoEscolhido() {
        var processar = mock(ProcessarNotificacaoPagamentoUseCase.class);
        var controller = new SimulacaoPagamentoController(simulador, processar);
        var orcamentoId = UUID.randomUUID();
        when(processar.executar(anyString())).thenReturn(DesfechoPagamento.APROVADO, DesfechoPagamento.RECUSADO);

        assertThat(controller.simular(orcamentoId, null).getBody()).contains("?resultado=aprovado", "?resultado=recusado");
        assertThat(controller.simular(orcamentoId, "aprovado").getBody()).contains("Pagamento aprovado");
        assertThat(controller.simular(orcamentoId, "RECUSADO").getBody()).contains("Pagamento não aprovado", "recusado");
        assertThatThrownBy(() -> controller.simular(orcamentoId, "talvez")).isInstanceOf(DomainException.class);
        verify(processar, times(2)).executar(anyString());
    }
}
