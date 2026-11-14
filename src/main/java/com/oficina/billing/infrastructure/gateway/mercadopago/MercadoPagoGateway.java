package com.oficina.billing.infrastructure.gateway.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.oficina.billing.application.exception.GatewayPagamentoException;
import com.oficina.billing.application.exception.GatewayPagamentoIndisponivelException;
import com.oficina.billing.application.port.CobrancaCriada;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PagamentoGateway;
import com.oficina.billing.application.port.SolicitacaoCobranca;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

@Component
@Profile("!local")
public class MercadoPagoGateway implements GatewayPagamento {

    public static final String RESILIENCIA = "mercadoPago";
    static final String CABECALHO_IDEMPOTENCIA = "X-Idempotency-Key";
    static final String CAMINHO_WEBHOOK = "/api/pagamentos/webhooks/mercado-pago";
    static final String SUCESSO = "sucesso";
    static final String FALHA = "falha";
    private static final DateTimeFormatter FORMATO_EXPIRACAO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx").withZone(ZoneOffset.UTC);

    private final RestClient cliente;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final MetricasBilling metricas;
    private final PropriedadesMercadoPago propriedades;
    private final String urlNotificacao;

    public MercadoPagoGateway(RestClient restClientMercadoPago,
                              RetryRegistry retryRegistry,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              MetricasBilling metricas,
                              PropriedadesMercadoPago propriedades,
                              @Value("${app.url-publica}") String urlPublica) {
        this.cliente = restClientMercadoPago;
        this.retry = retryRegistry.retry(RESILIENCIA);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCIA);
        this.metricas = metricas;
        this.propriedades = propriedades;
        this.urlNotificacao = urlPublica + CAMINHO_WEBHOOK;
    }

    @Override
    public CobrancaCriada criarCobranca(SolicitacaoCobranca solicitacao) {
        var corpo = preferenciaDe(solicitacao);
        var resposta = executar("criar_preferencia", () -> cliente.post()
            .uri("/checkout/preferences")
            .header(CABECALHO_IDEMPOTENCIA, solicitacao.orcamentoId().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(corpo)
            .retrieve()
            .body(PreferenciaResponse.class));
        if (resposta == null || resposta.id() == null) {
            throw new GatewayPagamentoException("Resposta do Mercado Pago sem identificador de preferência.", null);
        }
        var link = propriedades.usarSandboxInitPoint() ? resposta.sandboxInitPoint() : resposta.initPoint();
        return new CobrancaCriada(resposta.id(), link);
    }

    @Override
    public PagamentoGateway consultarPagamento(String pagamentoGatewayId) {
        var pagamento = executar("consultar_pagamento", () -> cliente.get()
            .uri("/v1/payments/{id}", pagamentoGatewayId)
            .retrieve()
            .body(JsonNode.class));
        if (pagamento == null) {
            throw new GatewayPagamentoException("Resposta vazia do Mercado Pago para o pagamento " + pagamentoGatewayId, null);
        }
        return converter(pagamento);
    }

    @Override
    public Optional<PagamentoGateway> buscarPorOrcamento(UUID orcamentoId) {
        var busca = executar("buscar_pagamentos", () -> cliente.get()
            .uri(uri -> uri.path("/v1/payments/search")
                .queryParam("external_reference", orcamentoId)
                .queryParam("sort", "date_created")
                .queryParam("criteria", "desc")
                .build())
            .retrieve()
            .body(JsonNode.class));
        if (busca == null) {
            return Optional.empty();
        }
        var resultados = StreamSupport.stream(busca.path("results").spliterator(), false)
            .map(this::converter)
            .toList();
        return resultados.stream()
            .filter(p -> p.status() == StatusPagamento.APROVADO)
            .findFirst()
            .or(() -> resultados.stream().findFirst());
    }

    @Override
    public void estornar(String pagamentoGatewayId, String chaveIdempotencia) {
        executar("estornar", () -> cliente.post()
            .uri("/v1/payments/{id}/refunds", pagamentoGatewayId)
            .header(CABECALHO_IDEMPOTENCIA, chaveIdempotencia)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .toBodilessEntity());
    }

    private <T> T executar(String operacao, Supplier<T> chamada) {
        var protegida = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, chamada));
        try {
            var resultado = protegida.get();
            metricas.registrarIntegracaoMercadoPago(operacao, SUCESSO);
            return resultado;
        } catch (CallNotPermittedException e) {
            metricas.registrarIntegracaoMercadoPago(operacao, FALHA);
            throw new GatewayPagamentoIndisponivelException("Integração com o Mercado Pago suspensa (circuito aberto).", e);
        } catch (HttpClientErrorException e) {
            metricas.registrarIntegracaoMercadoPago(operacao, FALHA);
            throw new GatewayPagamentoException(
                "Mercado Pago recusou a operação %s (HTTP %d).".formatted(operacao, e.getStatusCode().value()), e);
        } catch (RestClientException e) {
            metricas.registrarIntegracaoMercadoPago(operacao, FALHA);
            throw new GatewayPagamentoIndisponivelException(
                "Falha de comunicação com o Mercado Pago na operação %s.".formatted(operacao), e);
        }
    }

    private PreferenciaRequest preferenciaDe(SolicitacaoCobranca solicitacao) {
        var itens = solicitacao.itens().stream()
            .map(i -> new PreferenciaRequest.Item(i.id(), i.descricao(), i.quantidade(), i.precoUnitario(), "BRL"))
            .toList();
        var email = propriedades.emailPagadorTeste() == null || propriedades.emailPagadorTeste().isBlank()
            ? solicitacao.clienteEmail()
            : propriedades.emailPagadorTeste();
        var retorno = propriedades.urlRetorno().replace("{osId}", solicitacao.osId().toString());
        return new PreferenciaRequest(
            itens,
            new PreferenciaRequest.Pagador(email, solicitacao.clienteNome()),
            solicitacao.orcamentoId().toString(),
            urlNotificacao,
            new PreferenciaRequest.BackUrls(retorno, retorno, retorno),
            true,
            true,
            FORMATO_EXPIRACAO.format(solicitacao.prazo()));
    }

    private PagamentoGateway converter(JsonNode pagamento) {
        return new PagamentoGateway(
            texto(pagamento, "id"),
            MapeadorStatusMercadoPago.mapear(texto(pagamento, "status")),
            texto(pagamento, "status_detail"),
            texto(pagamento, "external_reference"),
            texto(pagamento, "payment_method_id"),
            instante(texto(pagamento, "date_approved")),
            pagamento.toString());
    }

    private static String texto(JsonNode no, String campo) {
        var valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
    }

    private static Instant instante(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
