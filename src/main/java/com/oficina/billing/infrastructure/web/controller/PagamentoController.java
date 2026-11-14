package com.oficina.billing.infrastructure.web.controller;

import com.oficina.billing.application.usecase.pagamento.ConsultarPagamentoUseCase;
import com.oficina.billing.infrastructure.security.Solicitante;
import com.oficina.billing.infrastructure.web.dto.response.PagamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pagamentos")
@Tag(name = "Pagamentos")
public class PagamentoController {

    private final ConsultarPagamentoUseCase consultar;

    public PagamentoController(ConsultarPagamentoUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar pagamento (cliente: apenas os próprios)")
    public ResponseEntity<PagamentoResponse> buscar(@PathVariable UUID id) {
        var solicitante = Solicitante.atual();
        var pagamento = solicitante.funcionario()
            ? consultar.buscar(id)
            : consultar.buscarDoCliente(id, solicitante.id());
        return ResponseEntity.ok(PagamentoResponse.from(pagamento));
    }
}
