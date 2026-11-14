package com.oficina.billing.integracao;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.oficina.billing.application.port.NotificacaoCliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracaoTestBase {

    protected static final String FILA_OS_EVENTOS = "oficina-local-os-eventos";
    protected static final String FILA_BILLING_COMANDOS = "oficina-local-billing-comandos";
    protected static final String FILA_DLQ = FILA_BILLING_COMANDOS + "-dlq";
    protected static final String SEGREDO_WEBHOOK = "segredo-webhook-de-teste";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("billing_test")
        .withUsername("billing")
        .withPassword("billing");

    static final LocalStackContainer LOCALSTACK =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.SQS);

    protected static final WireMockServer MERCADO_PAGO = new WireMockServer(wireMockConfig().dynamicPort());

    protected static final SqsClient SQS;

    private static final List<EventoRecebido> RECEBIDOS = new CopyOnWriteArrayList<>();

    private static final ObjectMapper LEITOR_EXATO = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
        .build();

    static {
        POSTGRES.start();
        LOCALSTACK.start();
        MERCADO_PAGO.start();
        SQS = SqsClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .build();
        criarFilaComDlq(FILA_OS_EVENTOS);
        criarFilaComDlq(FILA_BILLING_COMANDOS);
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.cloud.aws.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
        registro.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registro.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
        registro.add("spring.cloud.aws.sqs.listener.poll-timeout", () -> "1s");
        registro.add("app.mercado-pago.url-base", MERCADO_PAGO::baseUrl);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Value("${jwt.secret}") protected String jwtSecret;
    @MockitoBean protected NotificacaoCliente notificacao;

    @BeforeEach
    void reiniciarMercadoPago() {
        MERCADO_PAGO.resetAll();
    }

    protected record EventoRecebido(JsonNode envelope, Map<String, MessageAttributeValue> atributos) {
        public String tipo() {
            return envelope.path("tipo").asText();
        }

        public JsonNode dados() {
            return envelope.path("dados");
        }
    }

    protected record OrcamentoCriado(UUID osId, UUID sagaId, UUID orcamentoId, String token, UUID clienteId) {}

    protected OrcamentoCriado criarOrcamentoPelaFila(UUID clienteId) {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        enviarComando("GerarOrcamento", UUID.randomUUID(), sagaId, osId, dadosGerarOrcamento(clienteId));
        var gerado = aguardarEvento("OrcamentoGerado", osId);
        var token = ArgumentCaptor.forClass(String.class);
        verify(notificacao, timeout(5000)).enviarOrcamento(
            argThat(o -> o != null && osId.equals(o.getOsId())), token.capture());
        return new OrcamentoCriado(osId, sagaId, UUID.fromString(gerado.dados().path("orcamentoId").asText()),
            token.getValue(), clienteId);
    }

    protected String aprovarEPagar(OrcamentoCriado criado, String pagamentoMercadoPagoId) throws Exception {
        stubPreferencia(criado.orcamentoId());
        var resposta = mvc.perform(post("/api/orcamentos/{id}/aprovacao", criado.orcamentoId())
                .header("Authorization", "Bearer " + tokenCliente(criado.clienteId())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var pagamentoId = mapper.readTree(resposta).path("pagamento").path("id").asText();
        aguardarEvento("OrcamentoAprovado", criado.osId());
        stubPagamento(pagamentoMercadoPagoId, "approved", criado.orcamentoId());
        enviarWebhookAssinado(pagamentoMercadoPagoId).andExpect(status().isOk());
        aguardarEvento("PagamentoAprovado", criado.osId());
        return pagamentoId;
    }

    protected ResultActions enviarWebhookAssinado(String pagamentoMercadoPagoId) throws Exception {
        var requestId = UUID.randomUUID().toString();
        var ts = String.valueOf(Instant.now().toEpochMilli());
        return mvc.perform(post("/api/pagamentos/webhooks/mercado-pago")
            .param("data.id", pagamentoMercadoPagoId)
            .param("type", "payment")
            .header("x-signature", assinarWebhook(pagamentoMercadoPagoId, requestId, ts))
            .header("x-request-id", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"action":"payment.updated","api_version":"v1","type":"payment","data":{"id":"%s"},"live_mode":false}
                """.formatted(pagamentoMercadoPagoId)));
    }

    protected static void stubPreferencia(UUID orcamentoId) {
        MERCADO_PAGO.stubFor(WireMock.post("/checkout/preferences")
            .withHeader("X-Idempotency-Key", WireMock.equalTo(orcamentoId.toString()))
            .willReturn(WireMock.okJson("""
                {"id":"pref-%1$s","init_point":"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-%1$s",
                 "sandbox_init_point":"https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-%1$s"}
                """.formatted(orcamentoId))));
    }

    protected static void stubPagamento(String id, String status, UUID orcamentoId) {
        MERCADO_PAGO.stubFor(WireMock.get("/v1/payments/" + id).willReturn(WireMock.okJson(corpoPagamento(id, status, orcamentoId))));
    }

    protected static String corpoPagamento(String id, String status, UUID orcamentoId) {
        return """
            {"id":%s,"status":"%s","status_detail":"%s","external_reference":"%s",
             "payment_method_id":"master","transaction_amount":210.0,"date_approved":"2026-10-30T10:05:11.000-03:00"}
            """.formatted(id, status, "approved".equals(status) ? "accredited" : "cc_rejected_other_reason", orcamentoId);
    }

    protected static String dadosGerarOrcamento(UUID clienteId) {
        return """
            {"clienteId":"%s","cliente":{"nome":"Maria Souza","email":"maria@example.com"},
             "veiculo":{"placa":"ABC1D23","modelo":"Corolla"},
             "itens":[
               {"tipo":"SERVICO","referenciaId":"%s","descricao":"Troca de óleo","quantidade":1,"precoUnitario":120.00},
               {"tipo":"PECA","referenciaId":"%s","descricao":"Filtro de óleo","quantidade":2,"precoUnitario":45.00}
             ]}
            """.formatted(clienteId, UUID.randomUUID(), UUID.randomUUID());
    }

    protected static void enviarComando(String tipo, UUID mensagemId, UUID sagaId, UUID osId, String dados) {
        var corpo = """
            {"mensagemId":"%s","tipo":"%s","versao":1,"sagaId":"%s","osId":"%s","causadaPor":null,
             "origem":"os-service","ocorridoEm":"%s","dados":%s}
            """.formatted(mensagemId, tipo, sagaId, osId, Instant.now(), dados);
        enviarCorpo(FILA_BILLING_COMANDOS, corpo, tipo);
    }

    protected static void enviarCorpo(String fila, String corpo, String tipo) {
        SQS.sendMessage(envio -> envio
            .queueUrl(url(fila))
            .messageBody(corpo)
            .messageAttributes(Map.of(
                "tipo", MessageAttributeValue.builder().dataType("String").stringValue(tipo).build(),
                "versao", MessageAttributeValue.builder().dataType("String").stringValue("1").build())));
    }

    protected EventoRecebido aguardarEvento(String tipo, UUID osId) {
        return await().atMost(Duration.ofSeconds(25)).pollInterval(Duration.ofMillis(200))
            .until(() -> retirarEvento(tipo, osId), Optional::isPresent)
            .orElseThrow();
    }

    protected List<EventoRecebido> eventosDaOs(UUID osId) {
        drenar();
        return RECEBIDOS.stream()
            .filter(e -> osId.toString().equals(e.envelope().path("osId").asText()))
            .toList();
    }

    private Optional<EventoRecebido> retirarEvento(String tipo, UUID osId) {
        drenar();
        var encontrado = RECEBIDOS.stream()
            .filter(e -> tipo.equals(e.tipo()) && osId.toString().equals(e.envelope().path("osId").asText()))
            .findFirst();
        encontrado.ifPresent(RECEBIDOS::remove);
        return encontrado;
    }

    private void drenar() {
        var mensagens = new ArrayList<Message>(SQS.receiveMessage(recebimento -> recebimento
            .queueUrl(url(FILA_OS_EVENTOS))
            .maxNumberOfMessages(10)
            .waitTimeSeconds(1)
            .messageAttributeNames("All")).messages());
        for (var mensagem : mensagens) {
            try {
                RECEBIDOS.add(new EventoRecebido(LEITOR_EXATO.readTree(mensagem.body()), mensagem.messageAttributes()));
            } catch (Exception e) {
                throw new IllegalStateException("Mensagem inválida em os-eventos: " + mensagem.body(), e);
            }
            SQS.deleteMessage(exclusao -> exclusao.queueUrl(url(FILA_OS_EVENTOS)).receiptHandle(mensagem.receiptHandle()));
        }
    }

    protected static String url(String fila) {
        return SQS.getQueueUrl(consulta -> consulta.queueName(fila)).queueUrl();
    }

    protected String tokenFuncionario() {
        return token(UUID.randomUUID().toString(), "FUNCIONARIO");
    }

    protected String tokenCliente(UUID clienteId) {
        return token(clienteId.toString(), "CLIENTE");
    }

    private String token(String sujeito, String papel) {
        var agora = Instant.now();
        return Jwts.builder()
            .subject(sujeito)
            .issuer("oficina-auth")
            .claim("role", papel)
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(900)))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    protected static String assinarWebhook(String dataId, String requestId, String ts) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SEGREDO_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var manifesto = "id:%s;request-id:%s;ts:%s;".formatted(dataId, requestId, ts);
            return "ts=%s,v1=%s".formatted(ts, HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void criarFilaComDlq(String nome) {
        var dlqUrl = SQS.createQueue(criacao -> criacao.queueName(nome + "-dlq")).queueUrl();
        var dlqArn = SQS.getQueueAttributes(consulta -> consulta.queueUrl(dlqUrl)
            .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        SQS.createQueue(criacao -> criacao.queueName(nome).attributes(Map.of(
            QueueAttributeName.VISIBILITY_TIMEOUT, "30",
            QueueAttributeName.REDRIVE_POLICY, "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}".formatted(dlqArn))));
    }
}
