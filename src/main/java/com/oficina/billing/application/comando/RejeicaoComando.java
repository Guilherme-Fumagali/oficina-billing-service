package com.oficina.billing.application.comando;

import com.oficina.billing.application.mensagem.Correlacao;

import java.util.UUID;

public record RejeicaoComando(UUID mensagemId, UUID sagaId, UUID osId, String comando, String motivo, String codigo) {

    public Correlacao correlacao() {
        return new Correlacao(sagaId, osId, mensagemId);
    }
}
