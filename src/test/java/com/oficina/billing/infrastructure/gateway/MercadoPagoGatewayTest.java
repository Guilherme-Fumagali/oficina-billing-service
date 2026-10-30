package com.oficina.billing.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.oficina.billing.application.exception.GatewayPagamentoException;
import com.oficina.billing.application.exception.GatewayPagamentoIndisponivelException;
import com.oficina.billing.application.port.SolicitacaoCobranca;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.infrastructure.gateway.mercadopago.MercadoPagoConfig;
import com.oficina.billing.infrastructure.gateway.mercadopago.MercadoPagoGateway;
import com.oficina.billing.infrastructure.gateway.mercadopago.PropriedadesMercadoPago;
import com.oficina.billing.infrastructure.observability.MicrometerMetricasBilling;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoGatewayTest {

    @RegisterExtension
    static WireMockExtension mercadoPago = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private static final UUID ORCAMENTO_ID = UUID.fromString("5d2f1a60-0c1e-4b8e-9a4f-4f7e2c0f9a11");
    private static final UUID OS_ID = UUID.fromString("c3a1d8a2-5e8f-4b3a-9d1c-2b7e6f4a9e10");
    private static final Instant PRAZO = Instant.parse("2026-10-31T12:00:00Z");

    private SimpleMeterRegistry registry;
    private MercadoPagoGateway gateway;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gateway = gateway(propriedades(false, null, Duration.ofSeconds(2)));
    }

    @Test
    void criaPreferenciaDoCheckoutProComIdempotenciaENotificacaoPorPreferencia() throws Exception {
        mercadoPago.stubFor(post("/checkout/preferences").willReturn(okJson("""
            {"id":"123-pref","init_point":"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=123-pref",
             "sandbox_init_point":"https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=123-pref"}
            """)));

        var cobranca = gateway.criarCobranca(solicitacao());

        assertThat(cobranca.preferenciaId()).isEqualTo("123-pref");
        assertThat(cobranca.linkPagamento()).startsWith("https://www.mercadopago.com.br/");
        mercadoPago.verify(postRequestedFor(urlEqualTo("/checkout/preferences"))
            .withHeader("Authorization", equalTo("Bearer TEST-token"))
            .withHeader("X-Idempotency-Key", equalTo(ORCAMENTO_ID.toString()))
            .withRequestBody(matchingJsonPath("$.external_reference", equalTo(ORCAMENTO_ID.toString())))
            .withRequestBody(matchingJsonPath("$.notification_url",
                equalTo("http://billing.teste/api/pagamentos/webhooks/mercado-pago")))
            .withRequestBody(matchingJsonPath("$.binary_mode", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.expires", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.expiration_date_to", equalTo("2026-10-31T12:00:00.000+00:00")))
            .withRequestBody(matchingJsonPath("$.payer.email", equalTo("maria@example.com")))
            .withRequestBody(matchingJsonPath("$.back_urls.success",
                equalTo("http://billing.teste/api/ordens/" + OS_ID + "/status")))
            .withRequestBody(matchingJsonPath("$.items[0].currency_id", equalTo("BRL"))));
        var corpo = new ObjectMapper().readTree(mercadoPago.getAllServeEvents().get(0).getRequest().getBodyAsString());
        assertThat(corpo.path("items").get(0).path("unit_price").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(corpo.path("items").get(1).path("quantity").asInt()).isEqualTo(2);
        assertThat(corpo.path("items").get(1).path("title").asText()).isEqualTo("Filtro de óleo");
        assertThat(contador("criar_preferencia", "sucesso")).isEqualTo(1);
    }

    @Test
    void usaOLinkDeSandboxEOEmailDoPagadorDeTesteQuandoConfigurado() {
        var sandbox = gateway(propriedades(true, "test_user_123@testuser.com", Duration.ofSeconds(2)));
        mercadoPago.stubFor(post("/checkout/preferences").willReturn(okJson("""
            {"id":"456-pref","init_point":"https://prod/link","sandbox_init_point":"https://sandbox/link"}
            """)));

        var cobranca = sandbox.criarCobranca(solicitacao());

        assertThat(cobranca.linkPagamento()).isEqualTo("https://sandbox/link");
        mercadoPago.verify(postRequestedFor(urlEqualTo("/checkout/preferences"))
            .withRequestBody(matchingJsonPath("$.payer.email", equalTo("test_user_123@testuser.com"))));
    }

    @Test
    void respostaSemIdentificadorDePreferenciaEFalha() {
        mercadoPago.stubFor(post("/checkout/preferences").willReturn(okJson("{}")));
        var solicitacao = solicitacao();

        assertThatThrownBy(() -> gateway.criarCobranca(solicitacao)).isInstanceOf(GatewayPagamentoException.class);
    }

    @Test
    void consultaPagamentoPeloIdentificador() {
        mercadoPago.stubFor(get("/v1/payments/1319548861").willReturn(okJson("""
            {"id":1319548861,"status":"approved","status_detail":"accredited",
             "external_reference":"%s","payment_method_id":"master",
             "date_approved":"2026-10-30T10:05:11.000-04:00","transaction_amount":210.0}
            """.formatted(ORCAMENTO_ID))));

        var pagamento = gateway.consultarPagamento("1319548861");

        assertThat(pagamento.id()).isEqualTo("1319548861");
        assertThat(pagamento.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.statusDetalhe()).isEqualTo("accredited");
        assertThat(pagamento.orcamentoId()).contains(ORCAMENTO_ID);
        assertThat(pagamento.meio()).isEqualTo("master");
        assertThat(pagamento.aprovadoEm()).isEqualTo(Instant.parse("2026-10-30T14:05:11Z"));
        assertThat(pagamento.payload()).contains("1319548861");
    }

    @Test
    void consultaToleraCamposAusentesOuDataInvalida() {
        mercadoPago.stubFor(get("/v1/payments/77").willReturn(okJson("""
            {"id":77,"status":"pending","date_approved":"ontem","external_reference":null}
            """)));

        var pagamento = gateway.consultarPagamento("77");

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.aprovadoEm()).isNull();
        assertThat(pagamento.orcamentoId()).isEmpty();
    }

    @Test
    void buscaPorOrcamentoPriorizaOPagamentoAprovado() {
        mercadoPago.stubFor(get(urlPathEqualTo("/v1/payments/search"))
            .withQueryParam("external_reference", equalTo(ORCAMENTO_ID.toString()))
            .withQueryParam("sort", equalTo("date_created"))
            .withQueryParam("criteria", equalTo("desc"))
            .willReturn(okJson("""
                {"results":[
                  {"id":2,"status":"rejected","status_detail":"cc_rejected_other_reason","external_reference":"%1$s"},
                  {"id":1,"status":"approved","status_detail":"accredited","external_reference":"%1$s"}
                ]}
                """.formatted(ORCAMENTO_ID))));

        var encontrado = gateway.buscarPorOrcamento(ORCAMENTO_ID);

        assertThat(encontrado).get().satisfies(p -> {
            assertThat(p.id()).isEqualTo("1");
            assertThat(p.status()).isEqualTo(StatusPagamento.APROVADO);
        });
    }

    @Test
    void buscaSemAprovadoDevolveOMaisRecenteOuVazio() {
        mercadoPago.stubFor(get(urlPathEqualTo("/v1/payments/search"))
            .withQueryParam("external_reference", equalTo(ORCAMENTO_ID.toString()))
            .willReturn(okJson("""
                {"results":[{"id":9,"status":"in_process","external_reference":"%s"},{"id":8,"status":"rejected"}]}
                """.formatted(ORCAMENTO_ID))));
        var semResultados = UUID.randomUUID();
        mercadoPago.stubFor(get(urlPathEqualTo("/v1/payments/search"))
            .withQueryParam("external_reference", equalTo(semResultados.toString()))
            .willReturn(okJson("{\"results\":[]}")));

        assertThat(gateway.buscarPorOrcamento(ORCAMENTO_ID)).get().extracting(p -> p.id()).isEqualTo("9");
        assertThat(gateway.buscarPorOrcamento(semResultados)).isEmpty();
    }

    @Test
    void estornoTotalComChaveDeIdempotencia() {
        mercadoPago.stubFor(post("/v1/payments/1319548861/refunds").willReturn(aResponse().withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"id\":99,\"payment_id\":1319548861,\"status\":\"approved\"}")));

        gateway.estornar("1319548861", "pagamento-abc");

        mercadoPago.verify(postRequestedFor(urlEqualTo("/v1/payments/1319548861/refunds"))
            .withHeader("X-Idempotency-Key", equalTo("pagamento-abc"))
            .withRequestBody(equalToJson("{}")));
        assertThat(contador("estornar", "sucesso")).isEqualTo(1);
    }

    @Test
    void erro5xxERepetidoAteDarCerto() {
        mercadoPago.stubFor(get("/v1/payments/5").inScenario("instavel").whenScenarioStateIs(STARTED)
            .willReturn(serverError()).willSetStateTo("segunda"));
        mercadoPago.stubFor(get("/v1/payments/5").inScenario("instavel").whenScenarioStateIs("segunda")
            .willReturn(aResponse().withStatus(503)).willSetStateTo("terceira"));
        mercadoPago.stubFor(get("/v1/payments/5").inScenario("instavel").whenScenarioStateIs("terceira")
            .willReturn(okJson("{\"id\":5,\"status\":\"approved\"}")));

        var pagamento = gateway.consultarPagamento("5");

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.APROVADO);
        mercadoPago.verify(3, getRequestedFor(urlEqualTo("/v1/payments/5")));
    }

    @Test
    void erro5xxPersistenteViraGatewayIndisponivelAposTresTentativas() {
        mercadoPago.stubFor(get("/v1/payments/6").willReturn(serverError()));

        assertThatThrownBy(() -> gateway.consultarPagamento("6"))
            .isInstanceOf(GatewayPagamentoIndisponivelException.class);

        mercadoPago.verify(3, getRequestedFor(urlEqualTo("/v1/payments/6")));
        assertThat(contador("consultar_pagamento", "falha")).isEqualTo(1);
    }

    @Test
    void erro4xxNaoERepetido() {
        mercadoPago.stubFor(get("/v1/payments/7").willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> gateway.consultarPagamento("7"))
            .isInstanceOf(GatewayPagamentoException.class)
            .isNotInstanceOf(GatewayPagamentoIndisponivelException.class)
            .hasMessageContaining("404");

        mercadoPago.verify(1, getRequestedFor(urlEqualTo("/v1/payments/7")));
        assertThat(contador("consultar_pagamento", "falha")).isEqualTo(1);
    }

    @Test
    void tempoEsgotadoTambemERepetido() {
        var lento = gateway(propriedades(false, null, Duration.ofMillis(300)));
        mercadoPago.stubFor(get("/v1/payments/8").willReturn(okJson("{\"id\":8}").withFixedDelay(1500)));

        assertThatThrownBy(() -> lento.consultarPagamento("8")).isInstanceOf(GatewayPagamentoIndisponivelException.class);

        mercadoPago.verify(3, getRequestedFor(urlEqualTo("/v1/payments/8")));
    }

    @Test
    void circuitoAbertoInterrompeAsChamadas() {
        mercadoPago.stubFor(get("/v1/payments/9").willReturn(serverError()));

        assertThatThrownBy(() -> gateway.consultarPagamento("9")).isInstanceOf(GatewayPagamentoIndisponivelException.class);
        assertThatThrownBy(() -> gateway.consultarPagamento("9"))
            .isInstanceOf(GatewayPagamentoIndisponivelException.class)
            .hasMessageContaining("circuito aberto");
        assertThatThrownBy(() -> gateway.consultarPagamento("9"))
            .isInstanceOf(GatewayPagamentoIndisponivelException.class)
            .hasMessageContaining("circuito aberto");

        mercadoPago.verify(4, getRequestedFor(urlEqualTo("/v1/payments/9")));
        assertThat(contador("consultar_pagamento", "falha")).isEqualTo(3);
    }

    private MercadoPagoGateway gateway(PropriedadesMercadoPago propriedades) {
        var retry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
            .ignoreExceptions(HttpClientErrorException.class)
            .build());
        var circuito = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
            .ignoreExceptions(HttpClientErrorException.class)
            .build());
        return new MercadoPagoGateway(MercadoPagoConfig.criarRestClient(RestClient.builder(), propriedades),
            retry, circuito, new MicrometerMetricasBilling(registry), propriedades, "http://billing.teste");
    }

    private static PropriedadesMercadoPago propriedades(boolean sandbox, String emailTeste, Duration leitura) {
        return new PropriedadesMercadoPago(mercadoPago.baseUrl(), "TEST-token", "segredo", true, sandbox,
            "http://billing.teste/api/ordens/{osId}/status", emailTeste, Duration.ofSeconds(1), leitura);
    }

    private static SolicitacaoCobranca solicitacao() {
        return new SolicitacaoCobranca(ORCAMENTO_ID, OS_ID, "Maria Souza", "maria@example.com", List.of(
            new SolicitacaoCobranca.ItemCobranca("srv-1", "Troca de óleo", 1, new BigDecimal("120.00")),
            new SolicitacaoCobranca.ItemCobranca("pec-1", "Filtro de óleo", 2, new BigDecimal("45.00"))), PRAZO);
    }

    private double contador(String operacao, String resultado) {
        var contador = registry.find("oficina.integracao.mercadopago")
            .tags("operacao", operacao, "resultado", resultado)
            .counter();
        return contador == null ? 0 : contador.count();
    }
}
