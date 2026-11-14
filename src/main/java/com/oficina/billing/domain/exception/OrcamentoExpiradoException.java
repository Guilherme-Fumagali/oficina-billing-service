package com.oficina.billing.domain.exception;

import java.util.UUID;

public class OrcamentoExpiradoException extends TransicaoInvalidaException {
    public OrcamentoExpiradoException(UUID orcamentoId) {
        super("O prazo de decisão do orçamento %s terminou.".formatted(orcamentoId));
    }
}
