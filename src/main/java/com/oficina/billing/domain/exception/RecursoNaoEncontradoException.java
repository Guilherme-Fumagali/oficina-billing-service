package com.oficina.billing.domain.exception;

import java.util.UUID;

public class RecursoNaoEncontradoException extends DomainException {
    public RecursoNaoEncontradoException(String recurso, UUID id) {
        super("%s não encontrado: %s".formatted(recurso, id));
    }
}
