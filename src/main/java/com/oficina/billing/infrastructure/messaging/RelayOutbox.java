package com.oficina.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransportType;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.Relogio;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

@Component
public class RelayOutbox {

    static final int TAMANHO_MAXIMO_ERRO = 500;

    private final OutboxJpaRepository outbox;
    private final SqsTemplate sqsTemplate;
    private final PropriedadesMensageria propriedades;
    private final TransactionTemplate transacao;
    private final MetricasBilling metricas;
    private final Relogio relogio;
    private final ObjectMapper mapper;

    public RelayOutbox(OutboxJpaRepository outbox,
                       SqsTemplate sqsTemplate,
                       PropriedadesMensageria propriedades,
                       PlatformTransactionManager transactionManager,
                       MetricasBilling metricas,
                       Relogio relogio,
                       ObjectMapper mapper) {
        this.outbox = outbox;
        this.sqsTemplate = sqsTemplate;
        this.propriedades = propriedades;
        this.transacao = new TransactionTemplate(transactionManager);
        this.metricas = metricas;
        this.relogio = relogio;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.mensageria.relay.intervalo:500}")
    public void publicarPendentes() {
        transacao.executeWithoutResult(status ->
            outbox.reivindicarPendentes(propriedades.relay().lote()).forEach(this::publicar));
    }

    @Trace(dispatcher = true)
    void publicar(OutboxJpaEntity linha) {
        NewRelic.setTransactionName("Mensageria", "Relay/" + linha.getTipo());
        try {
            var transacaoNewRelic = NewRelic.getAgent().getTransaction();
            transacaoNewRelic.acceptDistributedTraceHeaders(TransportType.Queue, CabecalhosNewRelic.de(lerCabecalhos(linha)));
            var saida = new CabecalhosNewRelic();
            transacaoNewRelic.insertDistributedTraceHeaders(saida);

            Map<String, Object> atributos = new HashMap<>(saida.comoMapa());
            atributos.put("tipo", linha.getTipo());
            atributos.put("versao", String.valueOf(linha.getVersao()));

            var fila = propriedades.nomeDaFila(linha.getFila());
            sqsTemplate.send(envio -> envio
                .queue(fila)
                .payload(linha.getPayload())
                .headers(atributos));

            linha.setPublicadaEm(utc(relogio.agora()));
            linha.setUltimoErro(null);
            metricas.registrarMensagemPublicada(linha.getTipo());
        } catch (RuntimeException e) {
            linha.setTentativas(linha.getTentativas() + 1);
            linha.setUltimoErro(truncar(e.getMessage()));
            metricas.registrarFalha("relay_outbox", linha.getId().toString(), e);
        }
    }

    private Map<String, String> lerCabecalhos(OutboxJpaEntity linha) {
        try {
            return mapper.readValue(linha.getCabecalhos(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static String truncar(String mensagem) {
        if (mensagem == null) {
            return "erro sem mensagem";
        }
        return mensagem.length() <= TAMANHO_MAXIMO_ERRO ? mensagem : mensagem.substring(0, TAMANHO_MAXIMO_ERRO);
    }
}
