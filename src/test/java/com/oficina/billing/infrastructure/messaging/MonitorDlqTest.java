package com.oficina.billing.infrastructure.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorDlqTest {

    @Test
    void falhaAoLerADlqMantemOUltimoValorEExpoeOGaugePorFila() {
        var sqs = mock(SqsAsyncClient.class);
        var registry = new SimpleMeterRegistry();
        var propriedades = new PropriedadesMensageria(Map.of(), "oficina-local-billing-comandos-dlq", null);
        when(sqs.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any())).thenReturn(CompletableFuture.failedFuture(
            QueueDoesNotExistException.builder().message("sem fila").build()));
        var monitor = new MonitorDlq(sqs, registry, propriedades);

        monitor.atualizar();

        assertThat(monitor.mensagensNaDlq()).isZero();
        assertThat(registry.get("oficina.mensageria.dlq.mensagens").tag("fila", "oficina-local-billing-comandos-dlq")
            .gauge().value()).isZero();
    }
}
