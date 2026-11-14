package com.oficina.billing.domain.exception;

public class TokenDecisaoInvalidoException extends DomainException {
    public TokenDecisaoInvalidoException() {
        super("Link de decisão inválido.");
    }
}
