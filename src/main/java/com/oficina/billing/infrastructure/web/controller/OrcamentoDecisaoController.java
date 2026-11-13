package com.oficina.billing.infrastructure.web.controller;

import com.oficina.billing.application.exception.GatewayPagamentoException;
import com.oficina.billing.application.usecase.orcamento.DecidirPorTokenUseCase;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.valueobject.DecisaoOrcamento;
import com.oficina.billing.infrastructure.web.PaginaHtml;
import com.oficina.billing.infrastructure.web.dto.request.DecisaoRequest;
import com.oficina.billing.infrastructure.web.dto.response.OrcamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orcamentos")
@Tag(name = "Orçamentos — decisão por link")
@SecurityRequirements
public class OrcamentoDecisaoController {

    private final DecidirPorTokenUseCase decidir;

    public OrcamentoDecisaoController(DecidirPorTokenUseCase decidir) {
        this.decidir = decidir;
    }

    @GetMapping(value = "/{id}/decisao", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Página de confirmação aberta pelo link do e-mail (pública, não altera o orçamento)")
    public ResponseEntity<String> confirmar(@PathVariable UUID id,
                                            @RequestParam String token,
                                            @RequestParam DecisaoOrcamento decisao) {
        var aprovar = decisao == DecisaoOrcamento.APROVAR;
        var cor = aprovar ? PaginaHtml.VERDE : PaginaHtml.VERMELHO;
        var formulario = """
            <form method="post" action="decisao" style="margin:28px 0 0;">
              <input type="hidden" name="token" value="%s">
              <input type="hidden" name="decisao" value="%s">
              %s
            </form>
            """.formatted(PaginaHtml.escapar(token), decisao.name(),
                PaginaHtml.botao(aprovar ? "Confirmar aprovação" : "Confirmar reprovação", cor));
        return PaginaHtml.resposta(PaginaHtml.pagina(cor, "?",
            aprovar ? "Aprovar o orçamento?" : "Reprovar o orçamento?",
            "Confirme a decisão para que ela seja registrada.", formulario));
    }

    @PostMapping(value = "/{id}/decisao", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar a decisão com o token recebido por e-mail (pública, protegida pelo token)")
    public ResponseEntity<OrcamentoResponse> decidir(@PathVariable UUID id, @Valid @RequestBody DecisaoRequest requisicao) {
        return ResponseEntity.ok(OrcamentoResponse.from(decidir.executar(id, requisicao.token(), requisicao.decisao())));
    }

    @PostMapping(value = "/{id}/decisao", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Registrar a decisão confirmada na página do e-mail (pública, protegida pelo token)")
    public ResponseEntity<String> decidirPeloFormulario(@PathVariable UUID id,
                                                        @RequestParam String token,
                                                        @RequestParam DecisaoOrcamento decisao) {
        try {
            var resultado = decidir.executar(id, token, decisao);
            return PaginaHtml.resposta(decisao == DecisaoOrcamento.APROVAR
                ? paginaAprovado(resultado.pagamento())
                : PaginaHtml.pagina(PaginaHtml.VERMELHO, "&#10005;", "Orçamento reprovado",
                    "Registramos que você não aprovou o orçamento. Nenhum serviço será executado.", ""));
        } catch (DomainException | GatewayPagamentoException e) {
            return PaginaHtml.resposta(PaginaHtml.pagina(PaginaHtml.AMBAR, "!", "Não foi possível registrar a decisão",
                e.getMessage() + " Se o problema persistir, entre em contato com a oficina.", ""));
        }
    }

    private static String paginaAprovado(Pagamento pagamento) {
        var botao = pagamento == null ? "" : """
            <div style="margin:28px 0 0;">%s</div>
            """.formatted(PaginaHtml.link("Pagar com Mercado Pago", pagamento.getLinkPagamento(), PaginaHtml.AZUL));
        return PaginaHtml.pagina(PaginaHtml.VERDE, "&#10003;", "Orçamento aprovado",
            "Recebemos sua aprovação. O serviço começa assim que o pagamento for confirmado.", botao);
    }
}
