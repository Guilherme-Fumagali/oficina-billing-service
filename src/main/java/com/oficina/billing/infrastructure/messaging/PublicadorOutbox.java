package com.oficina.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.NewRelic;
import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.EventoBilling;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

@Component
public class PublicadorOutbox implements PublicadorMensagens {

    private static final String INSERIR = """
        INSERT INTO outbox (id, fila, tipo, versao, os_id, payload, cabecalhos, criada_em, tentativas)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, 0)
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Relogio relogio;

    public PublicadorOutbox(JdbcTemplate jdbc, ObjectMapper mapper, Relogio relogio) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.relogio = relogio;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publicar(Correlacao correlacao, EventoBilling evento) {
        var agora = relogio.agora();
        var envelope = new MensagemEnvelope<>(UUID.randomUUID(), evento.tipo(), evento.versao(),
            correlacao.sagaId(), correlacao.osId(), correlacao.causadaPor(), MensagemEnvelope.ORIGEM,
            agora, evento);
        var cabecalhos = new CabecalhosNewRelic();
        NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(cabecalhos);
        jdbc.update(INSERIR, envelope.mensagemId(), PropriedadesMensageria.FILA_OS_EVENTOS, envelope.tipo(),
            envelope.versao(), envelope.osId(), json(envelope), json(cabecalhos.comoMapa()), utc(agora));
    }

    private String json(Object valor) {
        try {
            return mapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a mensagem do outbox.", e);
        }
    }
}
