package com.oficina.billing.infrastructure.security;

import com.oficina.billing.infrastructure.security.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SEGREDO = "chave-de-teste-super-longa-para-hmac-sha-256-minimo-32-chars";
    private static final String OUTRO_SEGREDO = "outra-chave-completamente-diferente-com-32-chars-no-minimo!!";

    private final JwtService service = new JwtService(SEGREDO);

    @Test
    void aceitaTokenDeClienteEmitidoPelaLambda() {
        var id = UUID.randomUUID().toString();
        var token = token(SEGREDO, "oficina-auth", id, null, 900);

        assertThat(service.isTokenValido(token)).isTrue();
        assertThat(service.extrairClienteId(token)).isEqualTo(id);
        assertThat(service.extrairRole(token)).isEqualTo("CLIENTE");
    }

    @Test
    void aceitaTokenDeFuncionario() {
        var token = token(SEGREDO, "oficina-auth", UUID.randomUUID().toString(), "FUNCIONARIO", 900);

        assertThat(service.isTokenValido(token)).isTrue();
        assertThat(service.extrairRole(token)).isEqualTo("FUNCIONARIO");
    }

    @Test
    void recusaChaveEmissorValidadePapelESujeitoInvalidos() {
        var id = UUID.randomUUID().toString();

        assertThat(service.isTokenValido(token(OUTRO_SEGREDO, "oficina-auth", id, null, 900))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "outro-emissor", id, null, 900))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "oficina-auth", id, null, -60))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "oficina-auth", id, "ADMIN", 900))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "oficina-auth", "nao-e-uuid", null, 900))).isFalse();
        assertThat(service.isTokenValido("lixo")).isFalse();
    }

    private static String token(String segredo, String emissor, String sujeito, String role, long segundos) {
        var agora = Instant.now();
        var builder = Jwts.builder()
            .subject(sujeito)
            .issuer(emissor)
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(segundos)))
            .signWith(Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8)));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }
}
