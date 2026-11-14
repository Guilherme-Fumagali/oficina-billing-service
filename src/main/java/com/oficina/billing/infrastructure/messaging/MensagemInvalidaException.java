package com.oficina.billing.infrastructure.messaging;

public class MensagemInvalidaException extends RuntimeException {
    public MensagemInvalidaException(String message, Throwable cause) { super(message, cause); }
}
