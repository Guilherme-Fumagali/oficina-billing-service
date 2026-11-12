package com.oficina.billing.infrastructure.gateway.mercadopago;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class MercadoPagoConfig {

    @Bean
    public RestClient restClientMercadoPago(RestClient.Builder builder, PropriedadesMercadoPago propriedades) {
        return criarRestClient(builder, propriedades);
    }

    public static RestClient criarRestClient(RestClient.Builder builder, PropriedadesMercadoPago propriedades) {
        var http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(propriedades.timeoutConexao())
            .build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(propriedades.timeoutLeitura());
        return builder
            .baseUrl(propriedades.urlBase())
            .requestFactory(fabrica)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + propriedades.accessToken())
            .build();
    }
}
