package com.oficina.billing.application.mensagem;

public sealed interface EventoBilling permits OrcamentoGerado, OrcamentoAprovado, OrcamentoReprovado,
    OrcamentoExpirado, OrcamentoCancelado, PagamentoAprovado, PagamentoRecusado, PagamentoEstornado,
    ComandoRejeitado {

    default String tipo() {
        return getClass().getSimpleName();
    }

    default int versao() {
        return 1;
    }
}
