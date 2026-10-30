package com.oficina.billing.infrastructure.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public record Solicitante(UUID id, boolean funcionario) {

    private static final String ROLE_FUNCIONARIO = "ROLE_FUNCIONARIO";

    public static Solicitante atual() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        var funcionario = autenticacao.getAuthorities().stream()
            .anyMatch(a -> ROLE_FUNCIONARIO.equals(a.getAuthority()));
        return new Solicitante(UUID.fromString(autenticacao.getName()), funcionario);
    }
}
