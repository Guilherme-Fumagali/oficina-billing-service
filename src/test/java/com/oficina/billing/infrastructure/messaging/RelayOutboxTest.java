package com.oficina.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.application.port.MetricasBilling;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelayOutboxTest {

    @Mock OutboxJpaRepository outbox;
    @Mock SqsTemplate sqsTemplate;
    @Mock PlatformTransactionManager transactionManager;
    @Mock MetricasBilling metricas;

    RelayOutbox relay;

    @BeforeEach
    void setUp() {
        var propriedades = new PropriedadesMensageria(Map.of("os-eventos", "oficina-local-os-eventos"), "dlq",
            new PropriedadesMensageria.Relay(50));
        relay = new RelayOutbox(outbox, sqsTemplate, propriedades, transactionManager, metricas, () -> AGORA, new ObjectMapper());
    }

    @Test
    void publicaEMarcaAsLinhasReivindicadas() {
        var linha = linha("{\"traceparent\":\"00-1\"}");
        when(outbox.reivindicarPendentes(50)).thenReturn(List.of(linha));

        relay.publicarPendentes();

        assertThat(linha.getPublicadaEm()).isEqualTo(LocalDateTime.parse("2026-10-30T12:00:00"));
        assertThat(linha.getTentativas()).isZero();
        verify(metricas).registrarMensagemPublicada("OrcamentoGerado");
    }

    @Test
    void falhaNoEnvioIncrementaTentativasEGuardaOErroTruncado() {
        var linha = linha("cabecalho-corrompido");
        when(outbox.reivindicarPendentes(50)).thenReturn(List.of(linha));
        var erro = new IllegalStateException("x".repeat(600));
        when(sqsTemplate.send(any())).thenThrow(erro);

        relay.publicarPendentes();

        assertThat(linha.getPublicadaEm()).isNull();
        assertThat(linha.getTentativas()).isEqualTo(1);
        assertThat(linha.getUltimoErro()).hasSize(500);
        verify(metricas).registrarFalha(eq("relay_outbox"), anyString(), eq(erro));
    }

    @Test
    void falhaSemMensagemGuardaTextoPadraoEFilaDesconhecidaTambemFalha() {
        var semMensagem = linha("{}");
        var filaDesconhecida = linha("{}");
        filaDesconhecida.setFila("fila-inexistente");
        when(outbox.reivindicarPendentes(50)).thenReturn(List.of(semMensagem, filaDesconhecida));
        when(sqsTemplate.send(any())).thenThrow(new IllegalStateException());

        relay.publicarPendentes();

        assertThat(semMensagem.getUltimoErro()).isEqualTo("erro sem mensagem");
        assertThat(filaDesconhecida.getUltimoErro()).contains("fila-inexistente");
    }

    private static OutboxJpaEntity linha(String cabecalhos) {
        var linha = new OutboxJpaEntity();
        linha.setId(UUID.randomUUID());
        linha.setFila("os-eventos");
        linha.setTipo("OrcamentoGerado");
        linha.setVersao(1);
        linha.setOsId(UUID.randomUUID());
        linha.setPayload("{\"tipo\":\"OrcamentoGerado\"}");
        linha.setCabecalhos(cabecalhos);
        linha.setCriadaEm(LocalDateTime.parse("2026-10-30T11:59:59"));
        return linha;
    }
}
