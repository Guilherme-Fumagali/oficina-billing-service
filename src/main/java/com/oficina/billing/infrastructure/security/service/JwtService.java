package com.oficina.billing.infrastructure.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ISSUER_ESPERADO = "oficina-auth";

    private static final Set<String> ROLES_CONHECIDOS = Set.of("CLIENTE", "FUNCIONARIO");

    private static final String ROLE_PADRAO = "CLIENTE";

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isTokenValido(String token) {
        try {
            var claims = parsearClaims(token);
            UUID.fromString(claims.getSubject());
            return ISSUER_ESPERADO.equals(claims.getIssuer())
                && ROLES_CONHECIDOS.contains(lerRole(claims));
        } catch (Exception e) {
            return false;
        }
    }

    public String extrairClienteId(String token) {
        return parsearClaims(token).getSubject();
    }

    public String extrairRole(String token) {
        return lerRole(parsearClaims(token));
    }

    private String lerRole(Claims claims) {
        var role = claims.get("role", String.class);
        return role == null ? ROLE_PADRAO : role;
    }

    private Claims parsearClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
