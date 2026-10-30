package com.oficina.billing.application.port;

import com.oficina.billing.domain.entity.Orcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SolicitacaoCobranca(
    UUID orcamentoId,
    UUID osId,
    String clienteNome,
    String clienteEmail,
    List<ItemCobranca> itens,
    Instant prazo
) {
    public record ItemCobranca(String id, String descricao, int quantidade, BigDecimal precoUnitario) {}

    public static SolicitacaoCobranca de(Orcamento orcamento, Instant prazo) {
        var itens = orcamento.getItens().stream()
            .map(i -> new ItemCobranca(i.getReferenciaId().toString(), i.getDescricao(), i.getQuantidade(), i.getPrecoUnitario()))
            .toList();
        return new SolicitacaoCobranca(orcamento.getId(), orcamento.getOsId(), orcamento.getClienteNome(),
            orcamento.getClienteEmail(), itens, prazo);
    }
}
