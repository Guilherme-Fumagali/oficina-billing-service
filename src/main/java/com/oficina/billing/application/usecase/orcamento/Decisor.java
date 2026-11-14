package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.valueobject.CanalDecisao;

import java.util.UUID;

public record Decisor(UUID clienteId, CanalDecisao canal) {

    public static Decisor cliente(UUID clienteId) {
        return new Decisor(clienteId, CanalDecisao.CLIENTE);
    }

    public static Decisor balcao() {
        return new Decisor(null, CanalDecisao.BALCAO);
    }

    public static Decisor link() {
        return new Decisor(null, CanalDecisao.LINK);
    }

    public boolean podeDecidir(Orcamento orcamento) {
        return clienteId == null || orcamento.pertenceAo(clienteId);
    }
}
