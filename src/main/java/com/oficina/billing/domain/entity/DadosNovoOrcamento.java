package com.oficina.billing.domain.entity;

import java.util.List;
import java.util.UUID;

public record DadosNovoOrcamento(
    UUID osId,
    UUID sagaId,
    UUID clienteId,
    String clienteNome,
    String clienteEmail,
    String veiculoPlaca,
    String veiculoModelo,
    List<ItemOrcamento> itens
) {}
