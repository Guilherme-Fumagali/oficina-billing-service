package com.oficina.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransportType;
import com.oficina.billing.application.comando.ComandoCancelarOrcamento;
import com.oficina.billing.application.comando.ComandoEstornarPagamento;
import com.oficina.billing.application.comando.RejeicaoComando;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.comando.TiposComando;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.usecase.comando.RejeitarComandoUseCase;
import com.oficina.billing.application.usecase.orcamento.CancelarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.GerarOrcamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.EstornarPagamentoUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class ConsumidorComandosBilling {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorComandosBilling.class);

    static final String CODIGO_DADOS_INVALIDOS = "DADOS_INVALIDOS";
    private static final String MDC_OS_ID = "os.id";
    private static final String MDC_SAGA_ID = "saga.id";
    private static final String MDC_MENSAGEM_ID = "mensagem.id";
    private static final TypeReference<MensagemEnvelope<JsonNode>> ENVELOPE = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final GerarOrcamentoUseCase gerarOrcamento;
    private final CancelarOrcamentoUseCase cancelarOrcamento;
    private final EstornarPagamentoUseCase estornarPagamento;
    private final RejeitarComandoUseCase rejeitarComando;
    private final MetricasBilling metricas;

    public ConsumidorComandosBilling(ObjectMapper mapper,
                                     GerarOrcamentoUseCase gerarOrcamento,
                                     CancelarOrcamentoUseCase cancelarOrcamento,
                                     EstornarPagamentoUseCase estornarPagamento,
                                     RejeitarComandoUseCase rejeitarComando,
                                     MetricasBilling metricas) {
        this.mapper = mapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        this.gerarOrcamento = gerarOrcamento;
        this.cancelarOrcamento = cancelarOrcamento;
        this.estornarPagamento = estornarPagamento;
        this.rejeitarComando = rejeitarComando;
        this.metricas = metricas;
    }

    @SqsListener(value = "${app.mensageria.filas.entrada}", maxConcurrentMessages = "5",
        maxMessagesPerPoll = "5", acknowledgementMode = "ON_SUCCESS")
    public void receber(Message<String> mensagem) {
        processar(mensagem.getPayload(), mensagem.getHeaders());
    }

    @Trace(dispatcher = true)
    ResultadoComando processar(String corpo, Map<String, Object> cabecalhos) {
        var envelope = lerEnvelope(corpo);
        NewRelic.setTransactionName("Mensageria", "Consumo/" + envelope.tipo());
        NewRelic.getAgent().getTransaction().acceptDistributedTraceHeaders(TransportType.Queue, CabecalhosNewRelic.de(cabecalhos));
        NewRelic.addCustomParameter(MDC_OS_ID, envelope.osId().toString());
        NewRelic.addCustomParameter(MDC_SAGA_ID, String.valueOf(envelope.sagaId()));
        NewRelic.addCustomParameter("mensagem.tipo", envelope.tipo());
        MDC.put(MDC_OS_ID, envelope.osId().toString());
        MDC.put(MDC_SAGA_ID, String.valueOf(envelope.sagaId()));
        MDC.put(MDC_MENSAGEM_ID, envelope.mensagemId().toString());
        try {
            var resultado = despachar(envelope);
            var rotulo = resultado.name().toLowerCase(Locale.ROOT);
            metricas.registrarMensagemConsumida(envelope.tipo(), rotulo);
            log.info("Mensagem {} do tipo {} {}", envelope.mensagemId(), envelope.tipo(), rotulo);
            return resultado;
        } catch (RuntimeException e) {
            metricas.registrarMensagemConsumida(envelope.tipo(), "erro");
            throw e;
        } finally {
            MDC.remove(MDC_OS_ID);
            MDC.remove(MDC_SAGA_ID);
            MDC.remove(MDC_MENSAGEM_ID);
        }
    }

    private ResultadoComando despachar(MensagemEnvelope<JsonNode> envelope) {
        return switch (envelope.tipo()) {
            case TiposComando.GERAR_ORCAMENTO -> converter(envelope, DadosGerarOrcamento.class)
                .map(dados -> gerarOrcamento.executar(dados.paraComando(envelope)))
                .orElseGet(() -> rejeitar(envelope));
            case TiposComando.CANCELAR_ORCAMENTO -> converter(envelope, DadosMotivo.class)
                .map(dados -> cancelarOrcamento.executar(new ComandoCancelarOrcamento(
                    envelope.mensagemId(), envelope.sagaId(), envelope.osId(), dados.motivo())))
                .orElseGet(() -> rejeitar(envelope));
            case TiposComando.ESTORNAR_PAGAMENTO -> converter(envelope, DadosMotivo.class)
                .map(dados -> estornarPagamento.executar(new ComandoEstornarPagamento(
                    envelope.mensagemId(), envelope.sagaId(), envelope.osId(), dados.motivo())))
                .orElseGet(() -> rejeitar(envelope));
            default -> {
                log.warn("Tipo de mensagem desconhecido na fila de comandos: {}", envelope.tipo());
                yield ResultadoComando.IGNORADA;
            }
        };
    }

    private ResultadoComando rejeitar(MensagemEnvelope<JsonNode> envelope) {
        return rejeitarComando.executar(new RejeicaoComando(envelope.mensagemId(), envelope.sagaId(), envelope.osId(),
            envelope.tipo(), "Dados do comando %s inválidos.".formatted(envelope.tipo()), CODIGO_DADOS_INVALIDOS));
    }

    private <T> Optional<T> converter(MensagemEnvelope<JsonNode> envelope, Class<T> tipo) {
        JsonNode dados = envelope.dados() == null || envelope.dados().isNull() ? mapper.createObjectNode() : envelope.dados();
        try {
            return Optional.ofNullable(mapper.treeToValue(dados, tipo));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Dados inválidos na mensagem {}: {}", envelope.mensagemId(), e.getMessage());
            return Optional.empty();
        }
    }

    private MensagemEnvelope<JsonNode> lerEnvelope(String corpo) {
        MensagemEnvelope<JsonNode> envelope;
        try {
            envelope = mapper.readValue(corpo, ENVELOPE);
        } catch (JsonProcessingException e) {
            throw new MensagemInvalidaException("Corpo da mensagem não é um envelope válido.", e);
        }
        if (envelope == null || envelope.mensagemId() == null || envelope.tipo() == null || envelope.osId() == null) {
            throw new MensagemInvalidaException("Envelope sem mensagemId, tipo ou osId.", null);
        }
        return envelope;
    }
}
