package com.oficina.billing.application.mensagem;

import com.oficina.billing.domain.entity.Orcamento;

import java.util.UUID;

public record Correlacao(UUID sagaId, UUID osId, UUID causadaPor) {

    public static Correlacao de(Orcamento orcamento) {
        return new Correlacao(orcamento.getSagaId(), orcamento.getOsId(), null);
    }

    public Correlacao causadaPor(UUID mensagemId) {
        return new Correlacao(sagaId, osId, mensagemId);
    }
}
