package com.oficina.billing.integracao;

import com.oficina.billing.infrastructure.persistence.repository.OrcamentoJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MensageriaIntegrationTest extends IntegracaoTestBase {

    @Autowired OrcamentoJpaRepository orcamentoJpa;
    @Autowired MeterRegistry registry;

    @Test
    void gerarOrcamentoNaFilaCriaOOrcamentoEPublicaOrcamentoGerado() {
        var clienteId = UUID.randomUUID();
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        var mensagemId = UUID.randomUUID();

        enviarComando("GerarOrcamento", mensagemId, sagaId, osId, dadosGerarOrcamento(clienteId));

        var evento = aguardarEvento("OrcamentoGerado", osId);
        assertThat(evento.envelope().path("sagaId").asText()).isEqualTo(sagaId.toString());
        assertThat(evento.envelope().path("causadaPor").asText()).isEqualTo(mensagemId.toString());
        assertThat(evento.envelope().path("origem").asText()).isEqualTo("billing-service");
        assertThat(evento.envelope().path("versao").asInt()).isEqualTo(1);
        assertThat(evento.envelope().path("ocorridoEm").asText()).endsWith("Z");
        assertThat(evento.dados().path("total").decimalValue()).isEqualTo(new BigDecimal("210.00"));
        assertThat(evento.dados().path("validoAte").asText()).endsWith("Z");
        assertThat(evento.atributos().get("tipo").stringValue()).isEqualTo("OrcamentoGerado");
        assertThat(evento.atributos().get("versao").stringValue()).isEqualTo("1");

        var orcamento = orcamentoJpa.findByOsId(osId).orElseThrow();
        assertThat(orcamento.getId()).hasToString(evento.dados().path("orcamentoId").asText());
        assertThat(orcamento.getTokenDecisaoHash()).hasSize(64);
        assertThat(orcamento.getItens()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT publicada_em IS NOT NULL FROM outbox WHERE os_id = ? AND tipo = 'OrcamentoGerado'",
            Boolean.class, osId)).isTrue();
    }

    @Test
    void duplicataExataEDescartadaEComandoRepetidoRespondeOMesmoOrcamento() {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        var mensagemId = UUID.randomUUID();
        var dados = dadosGerarOrcamento(UUID.randomUUID());

        enviarComando("GerarOrcamento", mensagemId, sagaId, osId, dados);
        var primeiro = aguardarEvento("OrcamentoGerado", osId);
        enviarComando("GerarOrcamento", mensagemId, sagaId, osId, dados);

        await().atMost(Duration.ofSeconds(15)).until(() -> registry.find("oficina.mensageria.consumidas")
            .tags("tipo", "GerarOrcamento", "resultado", "duplicada").counter() != null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensagens_processadas WHERE mensagem_id = ?", Integer.class, mensagemId))
            .isEqualTo(1);

        var novaMensagem = UUID.randomUUID();
        enviarComando("GerarOrcamento", novaMensagem, sagaId, osId, dados);
        var repetido = aguardarEvento("OrcamentoGerado", osId);

        assertThat(repetido.dados().path("orcamentoId").asText()).isEqualTo(primeiro.dados().path("orcamentoId").asText());
        assertThat(repetido.envelope().path("causadaPor").asText()).isEqualTo(novaMensagem.toString());
        assertThat(eventosDaOs(osId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orcamentos WHERE os_id = ?", Integer.class, osId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE os_id = ?", Integer.class, osId)).isEqualTo(2);
    }

    @Test
    void comandoComDadosInvalidosRecebeComandoRejeitado() {
        var osId = UUID.randomUUID();

        enviarComando("GerarOrcamento", UUID.randomUUID(), UUID.randomUUID(), osId,
            "{\"clienteId\":\"%s\",\"itens\":[]}".formatted(UUID.randomUUID()));

        var rejeitado = aguardarEvento("ComandoRejeitado", osId);
        assertThat(rejeitado.dados().path("comando").asText()).isEqualTo("GerarOrcamento");
        assertThat(rejeitado.dados().path("codigo").asText()).isEqualTo("DADOS_INVALIDOS");
        assertThat(orcamentoJpa.findByOsId(osId)).isEmpty();
    }

    @Test
    void cancelarOrcamentoRespondeOrcamentoCancelado() {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());

        enviarComando("CancelarOrcamento", UUID.randomUUID(), criado.sagaId(), criado.osId(), "{\"motivo\":\"REPROVACAO\"}");

        var cancelado = aguardarEvento("OrcamentoCancelado", criado.osId());
        assertThat(cancelado.dados().path("orcamentoId").asText()).isEqualTo(criado.orcamentoId().toString());
        assertThat(cancelado.dados().path("motivo").asText()).isEqualTo("REPROVACAO");
        assertThat(orcamentoJpa.findByOsId(criado.osId()).orElseThrow().getStatus().name()).isEqualTo("CANCELADO");
    }

    @Test
    void estornarPagamentoAprovadoChamaORefundERespondePagamentoEstornado() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());
        var pagamentoId = aprovarEPagar(criado, "880011");
        MERCADO_PAGO.stubFor(post("/v1/payments/880011/refunds").willReturn(aResponse().withStatus(201)
            .withHeader("Content-Type", "application/json").withBody("{\"id\":1,\"status\":\"approved\"}")));

        enviarComando("EstornarPagamento", UUID.randomUUID(), criado.sagaId(), criado.osId(), "{\"motivo\":\"FALHA_EXECUCAO\"}");

        var estornado = aguardarEvento("PagamentoEstornado", criado.osId());
        assertThat(estornado.dados().path("pagamentoId").asText()).isEqualTo(pagamentoId);
        assertThat(estornado.dados().path("valor").decimalValue()).isEqualTo(new BigDecimal("210.00"));
        MERCADO_PAGO.verify(postRequestedFor(urlEqualTo("/v1/payments/880011/refunds"))
            .withHeader("X-Idempotency-Key", equalTo(pagamentoId)));
        assertThat(jdbc.queryForObject("SELECT status FROM pagamentos WHERE id = ?::uuid", String.class, pagamentoId))
            .isEqualTo("ESTORNADO");
    }

    @Test
    void estornarSemPagamentoAprovadoRespondeValorZero() {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());

        enviarComando("EstornarPagamento", UUID.randomUUID(), criado.sagaId(), criado.osId(), "{}");

        var estornado = aguardarEvento("PagamentoEstornado", criado.osId());
        assertThat(estornado.dados().path("valor").decimalValue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(estornado.dados().path("pagamentoId").isNull()).isTrue();
        MERCADO_PAGO.verify(0, postRequestedFor(urlEqualTo("/v1/payments/880011/refunds")));
    }
}
