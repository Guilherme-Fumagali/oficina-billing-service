package com.oficina.billing.infrastructure.web;

import com.oficina.billing.application.exception.GatewayPagamentoException;
import com.oficina.billing.application.exception.GatewayPagamentoIndisponivelException;
import com.oficina.billing.infrastructure.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void conflitosDeConcorrenciaEIntegridadeViram409() {
        var concorrencia = handler.handleConcorrencia(new ObjectOptimisticLockingFailureException("Orcamento", "id"));
        var integridade = handler.handleIntegridade(new DataIntegrityViolationException("os_id duplicado"));

        assertThat(concorrencia.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(integridade.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(integridade.getBody().getDetail()).doesNotContain("os_id");
    }

    @Test
    void falhasDoGatewayViram503Ou502SemDetalhesInternos() {
        var indisponivel = handler.handleGatewayIndisponivel(new GatewayPagamentoIndisponivelException("timeout interno", null));
        var recusado = handler.handleGateway(new GatewayPagamentoException("HTTP 401 token", null));

        assertThat(indisponivel.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(indisponivel.getBody().getDetail()).doesNotContain("timeout interno");
        assertThat(recusado.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(recusado.getBody().getDetail()).doesNotContain("token");
    }

    @Test
    void erroInesperadoVira500SemStacktrace() {
        var resposta = handler.handleInesperado(new IllegalStateException("NullPointer em Classe.metodo"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getBody().getDetail()).doesNotContain("Classe.metodo");
    }
}
