package com.oficina.billing.infrastructure.web.controller;

import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.usecase.pagamento.ProcessarNotificacaoPagamentoUseCase;
import com.oficina.billing.infrastructure.gateway.mercadopago.PropriedadesMercadoPago;
import com.oficina.billing.infrastructure.gateway.mercadopago.ResultadoAssinatura;
import com.oficina.billing.infrastructure.gateway.mercadopago.ValidadorAssinaturaMercadoPago;
import com.oficina.billing.infrastructure.web.dto.request.NotificacaoMercadoPagoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

@RestController
@RequestMapping("/api/pagamentos/webhooks")
@Tag(name = "Pagamentos — webhook")
public class WebhookMercadoPagoController {

    static final String TIPO_PAGAMENTO = "payment";

    private final ValidadorAssinaturaMercadoPago validador;
    private final ProcessarNotificacaoPagamentoUseCase processarNotificacao;
    private final PropriedadesMercadoPago propriedades;
    private final MetricasBilling metricas;

    public WebhookMercadoPagoController(ValidadorAssinaturaMercadoPago validador,
                                        ProcessarNotificacaoPagamentoUseCase processarNotificacao,
                                        PropriedadesMercadoPago propriedades,
                                        MetricasBilling metricas) {
        this.validador = validador;
        this.processarNotificacao = processarNotificacao;
        this.propriedades = propriedades;
        this.metricas = metricas;
    }

    @PostMapping("/mercado-pago")
    @SecurityRequirements
    @Operation(summary = "Receber notificação do Mercado Pago (pública, assinatura HMAC no cabeçalho x-signature)")
    public ResponseEntity<ProblemDetail> receber(
            @RequestHeader(value = "x-signature", required = false) String assinatura,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataIdConsulta,
            @RequestParam(value = "type", required = false) String tipoConsulta,
            @RequestParam(value = "topic", required = false) String topico,
            @RequestBody(required = false) NotificacaoMercadoPagoRequest corpo) {

        var dataId = primeiroPreenchido(dataIdConsulta, corpo != null && corpo.data() != null ? corpo.data().id() : null);
        var tipo = primeiroPreenchido(tipoConsulta, topico, corpo != null ? corpo.type() : null);
        var verificacao = validador.verificar(assinatura, requestId, dataId);
        metricas.registrarWebhook(verificacao.rotulo());
        processarNotificacao.registrar(requestId, dataId, tipo, verificacao == ResultadoAssinatura.VALIDA);

        if (verificacao != ResultadoAssinatura.VALIDA && propriedades.exigirAssinatura()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida."));
        }
        if (dataId != null && ehPagamento(tipo, corpo)) {
            try {
                processarNotificacao.executar(dataId);
            } catch (RuntimeException e) {
                metricas.registrarFalha("webhook_mercado_pago", dataId, e);
            }
        }
        return ResponseEntity.ok().build();
    }

    private static boolean ehPagamento(String tipo, NotificacaoMercadoPagoRequest corpo) {
        return TIPO_PAGAMENTO.equals(tipo)
            || (corpo != null && corpo.action() != null && corpo.action().startsWith(TIPO_PAGAMENTO + "."));
    }

    private static String primeiroPreenchido(String... valores) {
        return Stream.of(valores).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    }
}
