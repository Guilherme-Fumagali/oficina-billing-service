package com.oficina.billing.application.usecase.pagamento;

public enum DesfechoPagamento {
    APROVADO,
    RECUSADO,
    ESTORNADO,
    EXPIRADO,
    PENDENTE,
    IGNORADO,
    ESTORNO_AUTOMATICO,
    SEM_PAGAMENTO
}
