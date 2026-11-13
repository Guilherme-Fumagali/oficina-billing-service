package com.oficina.billing.infrastructure.messaging;

import com.oficina.billing.application.comando.ComandoGerarOrcamento;
import com.oficina.billing.domain.valueobject.TipoItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DadosGerarOrcamento(UUID clienteId, Cliente cliente, Veiculo veiculo, List<Item> itens) {

    public record Cliente(String nome, String email) {}

    public record Veiculo(String placa, String modelo) {}

    public record Item(TipoItem tipo, UUID referenciaId, String descricao, Integer quantidade, BigDecimal precoUnitario) {}

    public ComandoGerarOrcamento paraComando(MensagemEnvelope<?> envelope) {
        var itensComando = itens == null ? null : itens.stream()
            .map(i -> i == null ? null
                : new ComandoGerarOrcamento.Item(i.tipo(), i.referenciaId(), i.descricao(), i.quantidade(), i.precoUnitario()))
            .toList();
        return new ComandoGerarOrcamento(envelope.mensagemId(), envelope.sagaId(), envelope.osId(), clienteId,
            cliente == null ? null : cliente.nome(), cliente == null ? null : cliente.email(),
            veiculo == null ? null : veiculo.placa(), veiculo == null ? null : veiculo.modelo(),
            itensComando);
    }
}
