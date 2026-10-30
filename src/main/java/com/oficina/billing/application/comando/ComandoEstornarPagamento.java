package com.oficina.billing.application.comando;

import com.oficina.billing.application.mensagem.Correlacao;

import java.util.UUID;

public record ComandoEstornarPagamento(UUID mensagemId, UUID sagaId, UUID osId, String motivo) {

    public Correlacao correlacao() {
        return new Correlacao(sagaId, osId, mensagemId);
    }
}
