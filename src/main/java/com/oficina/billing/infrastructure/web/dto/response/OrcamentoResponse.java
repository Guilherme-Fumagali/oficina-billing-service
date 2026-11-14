package com.oficina.billing.infrastructure.web.dto.response;

import com.oficina.billing.application.usecase.orcamento.OrcamentoComPagamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.domain.valueobject.TipoItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrcamentoResponse(
    UUID id,
    UUID osId,
    UUID clienteId,
    String clienteNome,
    String veiculoPlaca,
    String veiculoModelo,
    List<ItemResponse> itens,
    BigDecimal total,
    StatusOrcamento status,
    Instant criadoEm,
    Instant validoAte,
    CanalDecisao canalDecisao,
    Instant aprovadoEm,
    Instant reprovadoEm,
    Instant expiradoEm,
    Instant canceladoEm,
    PagamentoResponse pagamento
) {
    public record ItemResponse(TipoItem tipo, UUID referenciaId, String descricao, int quantidade,
                               BigDecimal precoUnitario, BigDecimal subtotal) {}

    public static OrcamentoResponse from(OrcamentoComPagamento consulta) {
        return from(consulta.orcamento(), PagamentoResponse.from(consulta.pagamento()));
    }

    public static OrcamentoResponse from(Orcamento orcamento, PagamentoResponse pagamento) {
        var itens = orcamento.getItens().stream()
            .map(i -> new ItemResponse(i.getTipo(), i.getReferenciaId(), i.getDescricao(), i.getQuantidade(),
                i.getPrecoUnitario(), i.getSubtotal()))
            .toList();
        return new OrcamentoResponse(orcamento.getId(), orcamento.getOsId(), orcamento.getClienteId(),
            orcamento.getClienteNome(), orcamento.getVeiculoPlaca(), orcamento.getVeiculoModelo(), itens,
            orcamento.getTotal(), orcamento.getStatus(), orcamento.getCriadoEm(), orcamento.getValidoAte(),
            orcamento.getCanalDecisao(), orcamento.getAprovadoEm(), orcamento.getReprovadoEm(),
            orcamento.getExpiradoEm(), orcamento.getCanceladoEm(), pagamento);
    }
}
