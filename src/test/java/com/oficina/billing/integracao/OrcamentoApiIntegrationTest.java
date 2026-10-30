package com.oficina.billing.integracao;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrcamentoApiIntegrationTest extends IntegracaoTestBase {

    @Test
    void clienteAprovaECobrancaEPublicadaComOLinkDoMercadoPago() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());
        stubPreferencia(criado.orcamentoId());

        mvc.perform(post("/api/orcamentos/{id}/aprovacao", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenCliente(criado.clienteId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.canalDecisao").value("CLIENTE"))
            .andExpect(jsonPath("$.pagamento.status").value("PENDENTE"))
            .andExpect(jsonPath("$.pagamento.linkPagamento").value(containsString("pref-" + criado.orcamentoId())));

        var aprovado = aguardarEvento("OrcamentoAprovado", criado.osId());
        assertThat(aprovado.dados().path("canal").asText()).isEqualTo("CLIENTE");
        assertThat(aprovado.dados().path("linkPagamento").asText()).contains("pref-" + criado.orcamentoId());
        assertThat(aprovado.envelope().path("causadaPor").isNull()).isTrue();
        MERCADO_PAGO.verify(postRequestedFor(urlEqualTo("/checkout/preferences")));
    }

    @Test
    void consultaRespeitaODonoDoOrcamento() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());

        mvc.perform(get("/api/orcamentos/{id}", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenCliente(criado.clienteId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(210.00))
            .andExpect(jsonPath("$.itens.length()").value(2))
            .andExpect(jsonPath("$.pagamento").doesNotExist());

        mvc.perform(get("/api/orcamentos/{id}", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenCliente(UUID.randomUUID())))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/orcamentos").param("osId", criado.osId().toString())
                .header("Authorization", "Bearer " + tokenFuncionario()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(criado.orcamentoId().toString()))
            .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"));

        mvc.perform(post("/api/orcamentos/{id}/aprovacao", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenCliente(UUID.randomUUID())))
            .andExpect(status().isNotFound());
    }

    @Test
    void rotasProtegidasExigemTokenValido() throws Exception {
        mvc.perform(get("/api/orcamentos/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(get("/api/pagamentos/{id}", UUID.randomUUID()).header("Authorization", "Bearer token-invalido"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void parametrosInvalidosRetornamBadRequest() throws Exception {
        var token = "Bearer " + tokenFuncionario();

        mvc.perform(get("/api/orcamentos").header("Authorization", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Parâmetro obrigatório ausente: osId"));
        mvc.perform(get("/api/orcamentos/nao-e-uuid").header("Authorization", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro id."));
        mvc.perform(post("/api/orcamentos/{id}/decisao", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decisao\":\"APROVAR\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("token")));
    }

    @Test
    void linkDoEmailAbreConfirmacaoSemEfeitoEFormularioAprova() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());
        stubPreferencia(criado.orcamentoId());

        mvc.perform(get("/api/orcamentos/{id}/decisao", criado.orcamentoId())
                .param("token", criado.token()).param("decisao", "APROVAR"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("name=\"decisao\" value=\"APROVAR\"")));
        mvc.perform(get("/api/orcamentos/{id}", criado.orcamentoId()).header("Authorization", "Bearer " + tokenFuncionario()))
            .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"));

        mvc.perform(post("/api/orcamentos/{id}/decisao", criado.orcamentoId())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", criado.token())
                .param("decisao", "APROVAR"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Orçamento aprovado")))
            .andExpect(content().string(containsString("Pagar com Mercado Pago")));

        var aprovado = aguardarEvento("OrcamentoAprovado", criado.osId());
        assertThat(aprovado.dados().path("canal").asText()).isEqualTo("LINK");

        mvc.perform(post("/api/orcamentos/{id}/decisao", criado.orcamentoId())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", criado.token())
                .param("decisao", "REPROVAR"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Não foi possível registrar a decisão")));
    }

    @Test
    void decisaoPorApiReprovaComTokenERecusaTokenInvalido() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());

        mvc.perform(post("/api/orcamentos/{id}/decisao", criado.orcamentoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token-errado\",\"decisao\":\"REPROVAR\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orcamentos/{id}/decisao", criado.orcamentoId())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", "token-errado").param("decisao", "REPROVAR"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Link de decisão inválido.")));

        mvc.perform(post("/api/orcamentos/{id}/decisao", criado.orcamentoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"decisao\":\"REPROVAR\"}".formatted(criado.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPROVADO"))
            .andExpect(jsonPath("$.canalDecisao").value("LINK"));

        var reprovado = aguardarEvento("OrcamentoReprovado", criado.osId());
        assertThat(reprovado.dados().path("canal").asText()).isEqualTo("LINK");

        mvc.perform(post("/api/orcamentos/{id}/reprovacao", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenFuncionario()))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void paginaDeConfirmacaoEscapaOToken() throws Exception {
        mvc.perform(get("/api/orcamentos/{id}/decisao", UUID.randomUUID())
                .param("token", "\"><script>alert(1)</script>").param("decisao", "REPROVAR"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
            .andExpect(content().string(containsString("&quot;&gt;&lt;script&gt;")));
    }

    @Test
    void webhookAssinadoConsultaOMercadoPagoEPublicaPagamentoAprovado() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());

        var pagamentoId = aprovarEPagar(criado, "1319548861");

        MERCADO_PAGO.verify(getRequestedFor(urlEqualTo("/v1/payments/1319548861")));
        mvc.perform(get("/api/pagamentos/{id}", pagamentoId).header("Authorization", "Bearer " + tokenCliente(criado.clienteId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.meio").value("master"));
        mvc.perform(get("/api/pagamentos/{id}", pagamentoId).header("Authorization", "Bearer " + tokenCliente(UUID.randomUUID())))
            .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM notificacoes_pagamento WHERE data_id = '1319548861' AND assinatura_valida", Integer.class))
            .isEqualTo(1);

        enviarWebhookAssinado("1319548861").andExpect(status().isOk());
        assertThat(eventosDaOs(criado.osId())).noneMatch(e -> "PagamentoAprovado".equals(e.tipo()));
    }

    @Test
    void webhookSemAssinaturaValidaERecusadoERegistrado() throws Exception {
        mvc.perform(post("/api/pagamentos/webhooks/mercado-pago")
                .param("data.id", "555").param("type", "payment")
                .header("x-signature", "ts=1,v1=abc").header("x-request-id", "req-invalida"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pagamentos/webhooks/mercado-pago").param("data.id", "556").param("type", "payment"))
            .andExpect(status().isUnauthorized());

        MERCADO_PAGO.verify(0, getRequestedFor(urlPathEqualTo("/v1/payments/555")));
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM notificacoes_pagamento WHERE data_id IN ('555', '556') AND NOT assinatura_valida", Integer.class))
            .isEqualTo(2);
    }

    @Test
    void webhookDeOutroTopicoEFalhaNaConsultaRespondem200() throws Exception {
        var requestId = "req-merchant";
        mvc.perform(post("/api/pagamentos/webhooks/mercado-pago")
                .param("data.id", "777").param("type", "merchant_order")
                .header("x-signature", assinarWebhook("777", requestId, "1700000000")).header("x-request-id", requestId))
            .andExpect(status().isOk());
        MERCADO_PAGO.verify(0, getRequestedFor(urlPathEqualTo("/v1/payments/777")));

        MERCADO_PAGO.stubFor(WireMock.get("/v1/payments/778").willReturn(serverError()));
        enviarWebhookAssinado("778").andExpect(status().isOk());
    }

    @Test
    void mercadoPagoForaDoArDeixaOOrcamentoAguardandoERetorna503() throws Exception {
        var criado = criarOrcamentoPelaFila(UUID.randomUUID());
        MERCADO_PAGO.stubFor(WireMock.post("/checkout/preferences").willReturn(serverError()));

        mvc.perform(post("/api/orcamentos/{id}/aprovacao", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenFuncionario()))
            .andExpect(status().isServiceUnavailable());

        mvc.perform(get("/api/orcamentos/{id}", criado.orcamentoId()).header("Authorization", "Bearer " + tokenFuncionario()))
            .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"));
    }

    @Test
    void actuatorExpoeSaudeEInformacoesSemAutenticacao() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void contratoOpenApiVersionadoDeveCorresponderAAplicacao() throws Exception {
        var resposta = mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var ordenado = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
        var contrato = (ObjectNode) ordenado.readTree(resposta);
        contrato.remove("servers");
        var gerado = ordenado.writeValueAsString(ordenado.convertValue(contrato, Object.class)) + "\n";

        var arquivo = Path.of("docs/openapi.json");
        if (Boolean.getBoolean("atualizarOpenApi")) {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, gerado, StandardCharsets.UTF_8);
        }

        assertThat(arquivo)
            .as("docs/openapi.json desatualizado: execute ./mvnw test -Dtest=OrcamentoApiIntegrationTest -DatualizarOpenApi=true")
            .exists()
            .content(StandardCharsets.UTF_8).isEqualTo(gerado);
    }
}
