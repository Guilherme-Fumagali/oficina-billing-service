package com.oficina.billing.infrastructure.gateway.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.mercado-pago")
public record PropriedadesMercadoPago(
    String urlBase,
    String accessToken,
    String webhookSecret,
    boolean exigirAssinatura,
    boolean usarSandboxInitPoint,
    String urlRetorno,
    String emailPagadorTeste,
    Duration timeoutConexao,
    Duration timeoutLeitura
) {}
