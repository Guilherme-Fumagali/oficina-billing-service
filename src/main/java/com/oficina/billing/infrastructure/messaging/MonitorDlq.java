package com.oficina.billing.infrastructure.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class MonitorDlq {

    private static final Logger log = LoggerFactory.getLogger(MonitorDlq.class);

    static final String METRICA = "oficina.mensageria.dlq.mensagens";

    private final SqsAsyncClient sqs;
    private final String fila;
    private final AtomicLong mensagens = new AtomicLong();

    public MonitorDlq(SqsAsyncClient sqs, MeterRegistry registry, PropriedadesMensageria propriedades) {
        this.sqs = sqs;
        this.fila = propriedades.dlq();
        Gauge.builder(METRICA, mensagens, AtomicLong::get)
            .tag("fila", fila)
            .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.mensageria.dlq-monitor.intervalo:60000}",
        initialDelayString = "${app.mensageria.dlq-monitor.atraso-inicial:15000}")
    public void atualizar() {
        try {
            var url = sqs.getQueueUrl(r -> r.queueName(fila)).join().queueUrl();
            var atributos = sqs.getQueueAttributes(r -> r.queueUrl(url)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)).join();
            mensagens.set(Long.parseLong(atributos.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)));
        } catch (RuntimeException e) {
            log.warn("Não foi possível ler a quantidade de mensagens da DLQ {}: {}", fila, e.getMessage());
        }
    }

    public long mensagensNaDlq() {
        return mensagens.get();
    }
}
