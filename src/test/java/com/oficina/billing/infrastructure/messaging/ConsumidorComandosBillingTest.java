package com.oficina.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oficina.billing.application.comando.ComandoCancelarOrcamento;
import com.oficina.billing.application.comando.ComandoEstornarPagamento;
import com.oficina.billing.application.comando.ComandoGerarOrcamento;
import com.oficina.billing.application.comando.RejeicaoComando;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.usecase.comando.RejeitarComandoUseCase;
import com.oficina.billing.application.usecase.orcamento.CancelarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.GerarOrcamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.EstornarPagamentoUseCase;
import com.oficina.billing.domain.valueobject.TipoItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumidorComandosBillingTest {

    @Mock GerarOrcamentoUseCase gerarOrcamento;
    @Mock CancelarOrcamentoUseCase cancelarOrcamento;
    @Mock EstornarPagamentoUseCase estornarPagamento;
    @Mock RejeitarComandoUseCase rejeitarComando;
    @Mock MetricasBilling metricas;

    ConsumidorComandosBilling consumidor;
    UUID mensagemId;
    UUID sagaId;
    UUID osId;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumidor = new ConsumidorComandosBilling(mapper, gerarOrcamento, cancelarOrcamento, estornarPagamento,
            rejeitarComando, metricas);
        mensagemId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
        osId = UUID.randomUUID();
    }

    @Test
    void gerarOrcamentoConverteODadosDoEnvelope() {
        var referencia = UUID.randomUUID();
        when(gerarOrcamento.executar(any())).thenReturn(ResultadoComando.APLICADA);

        var resultado = consumidor.processar(envelope("GerarOrcamento", """
            {"clienteId":"%s","cliente":{"nome":"Maria","email":"maria@example.com"},
             "veiculo":{"placa":"ABC1D23","modelo":"Corolla"},
             "itens":[{"tipo":"PECA","referenciaId":"%s","descricao":"Filtro","quantidade":2,"precoUnitario":45.00}],
             "campoNovo":"ignorado"}
            """.formatted(UUID.randomUUID(), referencia)), Map.of("traceparent", "00-1"));

        assertThat(resultado).isEqualTo(ResultadoComando.APLICADA);
        var comando = ArgumentCaptor.forClass(ComandoGerarOrcamento.class);
        verify(gerarOrcamento).executar(comando.capture());
        assertThat(comando.getValue().mensagemId()).isEqualTo(mensagemId);
        assertThat(comando.getValue().sagaId()).isEqualTo(sagaId);
        assertThat(comando.getValue().osId()).isEqualTo(osId);
        assertThat(comando.getValue().clienteEmail()).isEqualTo("maria@example.com");
        assertThat(comando.getValue().veiculoModelo()).isEqualTo("Corolla");
        assertThat(comando.getValue().itens()).singleElement().satisfies(item -> {
            assertThat(item.tipo()).isEqualTo(TipoItem.PECA);
            assertThat(item.referenciaId()).isEqualTo(referencia);
            assertThat(item.precoUnitario()).isEqualByComparingTo("45.00");
        });
        verify(metricas).registrarMensagemConsumida("GerarOrcamento", "aplicada");
    }

    @Test
    void gerarOrcamentoSemClienteEVeiculoChegaAoCasoDeUsoParaRejeicao() {
        when(gerarOrcamento.executar(any())).thenReturn(ResultadoComando.REJEITADA);

        consumidor.processar(envelope("GerarOrcamento", "{\"itens\":[null]}"), Map.of());

        var comando = ArgumentCaptor.forClass(ComandoGerarOrcamento.class);
        verify(gerarOrcamento).executar(comando.capture());
        assertThat(comando.getValue().clienteNome()).isNull();
        assertThat(comando.getValue().veiculoPlaca()).isNull();
        assertThat(comando.getValue().itens()).containsExactly((ComandoGerarOrcamento.Item) null);
        verify(metricas).registrarMensagemConsumida("GerarOrcamento", "rejeitada");
    }

    @Test
    void cancelarEEstornarRepassamOMotivo() {
        when(cancelarOrcamento.executar(any())).thenReturn(ResultadoComando.APLICADA);
        when(estornarPagamento.executar(any())).thenReturn(ResultadoComando.DUPLICADA);

        consumidor.processar(envelope("CancelarOrcamento", "{\"motivo\":\"PAGAMENTO_RECUSADO\"}"), Map.of());
        consumidor.processar(envelope("EstornarPagamento", null), Map.of());

        verify(cancelarOrcamento).executar(new ComandoCancelarOrcamento(mensagemId, sagaId, osId, "PAGAMENTO_RECUSADO"));
        verify(estornarPagamento).executar(new ComandoEstornarPagamento(mensagemId, sagaId, osId, null));
        verify(metricas).registrarMensagemConsumida("EstornarPagamento", "duplicada");
    }

    @Test
    void dadosComTipoIncompativelSaoRejeitadosComRespostaAoOrquestrador() {
        when(rejeitarComando.executar(any())).thenReturn(ResultadoComando.REJEITADA);

        var resultado = consumidor.processar(envelope("GerarOrcamento",
            "{\"itens\":[{\"tipo\":\"OUTRO\",\"quantidade\":\"muitas\"}]}"), Map.of());

        assertThat(resultado).isEqualTo(ResultadoComando.REJEITADA);
        var rejeicao = ArgumentCaptor.forClass(RejeicaoComando.class);
        verify(rejeitarComando).executar(rejeicao.capture());
        assertThat(rejeicao.getValue().comando()).isEqualTo("GerarOrcamento");
        assertThat(rejeicao.getValue().codigo()).isEqualTo("DADOS_INVALIDOS");
        verifyNoInteractions(gerarOrcamento);
    }

    @Test
    void tipoDesconhecidoEIgnorado() {
        assertThat(consumidor.processar(envelope("ComandoDoFuturo", "{}"), Map.of())).isEqualTo(ResultadoComando.IGNORADA);

        verify(metricas).registrarMensagemConsumida("ComandoDoFuturo", "ignorada");
    }

    @Test
    void envelopeInvalidoLancaExcecaoParaIrADlqAposAsTentativas() {
        var semOsId = """
            {"mensagemId":"%s","tipo":"GerarOrcamento","versao":1,"dados":{}}
            """.formatted(UUID.randomUUID());
        var cabecalhos = Map.<String, Object>of();

        assertThatThrownBy(() -> consumidor.processar("nao e json", cabecalhos)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> consumidor.processar(semOsId, cabecalhos)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> consumidor.processar("null", cabecalhos)).isInstanceOf(MensagemInvalidaException.class);
    }

    @Test
    void falhaTecnicaPropagaERegistraErro() {
        when(cancelarOrcamento.executar(any())).thenThrow(new IllegalStateException("banco fora"));
        var corpo = envelope("CancelarOrcamento", "{}");
        var cabecalhos = Map.<String, Object>of();

        assertThatThrownBy(() -> consumidor.processar(corpo, cabecalhos)).isInstanceOf(IllegalStateException.class);

        verify(metricas).registrarMensagemConsumida("CancelarOrcamento", "erro");
    }

    @Test
    void receberUsaOCorpoEOsCabecalhosDaMensagemSqs() {
        when(cancelarOrcamento.executar(any())).thenReturn(ResultadoComando.APLICADA);

        consumidor.receber(MessageBuilder.withPayload(envelope("CancelarOrcamento", "{\"motivo\":\"x\"}"))
            .setHeader("tipo", "CancelarOrcamento")
            .build());

        verify(cancelarOrcamento).executar(new ComandoCancelarOrcamento(mensagemId, sagaId, osId, "x"));
    }

    private String envelope(String tipo, String dados) {
        return """
            {"mensagemId":"%s","tipo":"%s","versao":1,"sagaId":"%s","osId":"%s","causadaPor":null,
             "origem":"os-service","ocorridoEm":"2026-10-30T14:05:11.482Z","dados":%s}
            """.formatted(mensagemId, tipo, sagaId, osId, dados);
    }
}
