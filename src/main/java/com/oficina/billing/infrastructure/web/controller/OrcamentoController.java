package com.oficina.billing.infrastructure.web.controller;

import com.oficina.billing.application.usecase.orcamento.AprovarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.ConsultarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.Decisor;
import com.oficina.billing.application.usecase.orcamento.ReprovarOrcamentoUseCase;
import com.oficina.billing.infrastructure.security.Solicitante;
import com.oficina.billing.infrastructure.web.dto.response.OrcamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orcamentos")
@Tag(name = "Orçamentos")
@SecurityRequirement(name = "bearerAuth")
public class OrcamentoController {

    private final ConsultarOrcamentoUseCase consultar;
    private final AprovarOrcamentoUseCase aprovar;
    private final ReprovarOrcamentoUseCase reprovar;

    public OrcamentoController(ConsultarOrcamentoUseCase consultar,
                               AprovarOrcamentoUseCase aprovar,
                               ReprovarOrcamentoUseCase reprovar) {
        this.consultar = consultar;
        this.aprovar = aprovar;
        this.reprovar = reprovar;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar orçamento e pagamento (cliente: apenas os próprios)")
    public ResponseEntity<OrcamentoResponse> buscar(@PathVariable UUID id) {
        var solicitante = Solicitante.atual();
        var consulta = solicitante.funcionario()
            ? consultar.buscar(id)
            : consultar.buscarDoCliente(id, solicitante.id());
        return ResponseEntity.ok(OrcamentoResponse.from(consulta));
    }

    @GetMapping
    @Operation(summary = "Consultar o orçamento de uma OS, com o pagamento (cliente: apenas os próprios)")
    public ResponseEntity<OrcamentoResponse> buscarPorOs(@RequestParam UUID osId) {
        var solicitante = Solicitante.atual();
        var consulta = solicitante.funcionario()
            ? consultar.buscarPorOs(osId)
            : consultar.buscarPorOsDoCliente(osId, solicitante.id());
        return ResponseEntity.ok(OrcamentoResponse.from(consulta));
    }

    @PostMapping("/{id}/aprovacao")
    @Operation(summary = "Aprovar o orçamento e gerar a cobrança no Mercado Pago (cliente dono ou funcionário no balcão)")
    public ResponseEntity<OrcamentoResponse> aprovar(@PathVariable UUID id) {
        return ResponseEntity.ok(OrcamentoResponse.from(aprovar.executar(id, decisorAtual())));
    }

    @PostMapping("/{id}/reprovacao")
    @Operation(summary = "Reprovar o orçamento (cliente dono ou funcionário no balcão)")
    public ResponseEntity<OrcamentoResponse> reprovar(@PathVariable UUID id) {
        return ResponseEntity.ok(OrcamentoResponse.from(reprovar.executar(id, decisorAtual()), null));
    }

    private static Decisor decisorAtual() {
        var solicitante = Solicitante.atual();
        return solicitante.funcionario() ? Decisor.balcao() : Decisor.cliente(solicitante.id());
    }
}
