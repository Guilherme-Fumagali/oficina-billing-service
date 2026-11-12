package com.oficina.billing.infrastructure.web.controller;

import com.oficina.billing.application.usecase.pagamento.DesfechoPagamento;
import com.oficina.billing.application.usecase.pagamento.ProcessarNotificacaoPagamentoUseCase;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.infrastructure.gateway.simulado.GatewayPagamentoSimulado;
import com.oficina.billing.infrastructure.web.PaginaHtml;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@Profile("local")
@Hidden
@RequestMapping("/api/pagamentos/simulacao")
public class SimulacaoPagamentoController {

    private final GatewayPagamentoSimulado simulador;
    private final ProcessarNotificacaoPagamentoUseCase processarNotificacao;

    public SimulacaoPagamentoController(GatewayPagamentoSimulado simulador,
                                        ProcessarNotificacaoPagamentoUseCase processarNotificacao) {
        this.simulador = simulador;
        this.processarNotificacao = processarNotificacao;
    }

    @GetMapping(value = "/{orcamentoId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> simular(@PathVariable UUID orcamentoId,
                                         @RequestParam(required = false) String resultado) {
        if (resultado == null || resultado.isBlank()) {
            var opcoes = """
                <div style="margin:28px 0 0;">%s%s</div>
                """.formatted(
                    PaginaHtml.link("Aprovar pagamento", "?resultado=aprovado", PaginaHtml.VERDE),
                    PaginaHtml.link("Recusar pagamento", "?resultado=recusado", PaginaHtml.VERMELHO));
            return PaginaHtml.resposta(PaginaHtml.pagina(PaginaHtml.AZUL, "R$", "Pagamento simulado",
                "Ambiente local: escolha o resultado do pagamento do orçamento.", opcoes));
        }
        var aprovado = switch (resultado.toLowerCase(Locale.ROOT)) {
            case "aprovado" -> true;
            case "recusado" -> false;
            default -> throw new DomainException("O resultado deve ser aprovado ou recusado.");
        };
        var desfecho = processarNotificacao.executar(simulador.simular(orcamentoId, aprovado));
        return PaginaHtml.resposta(desfecho == DesfechoPagamento.APROVADO
            ? PaginaHtml.pagina(PaginaHtml.VERDE, "&#10003;", "Pagamento aprovado", "O pagamento simulado foi confirmado.", "")
            : PaginaHtml.pagina(PaginaHtml.VERMELHO, "&#10005;", "Pagamento não aprovado",
                "Resultado da simulação: " + desfecho.name().toLowerCase(Locale.ROOT) + ".", ""));
    }
}
