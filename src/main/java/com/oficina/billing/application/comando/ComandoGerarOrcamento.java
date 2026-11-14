package com.oficina.billing.application.comando;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.domain.valueobject.TipoItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ComandoGerarOrcamento(
    UUID mensagemId,
    UUID sagaId,
    UUID osId,
    UUID clienteId,
    String clienteNome,
    String clienteEmail,
    String veiculoPlaca,
    String veiculoModelo,
    List<Item> itens
) {
    public record Item(TipoItem tipo, UUID referenciaId, String descricao, Integer quantidade, BigDecimal precoUnitario) {}

    public Correlacao correlacao() {
        return new Correlacao(sagaId, osId, mensagemId);
    }
}
