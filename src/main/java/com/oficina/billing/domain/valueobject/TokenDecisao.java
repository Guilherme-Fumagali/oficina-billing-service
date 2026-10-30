package com.oficina.billing.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenDecisao {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAMANHO_BYTES = 32;

    private final String valor;
    private final String hash;

    private TokenDecisao(String valor) {
        this.valor = valor;
        this.hash = calcularHash(valor);
    }

    public static TokenDecisao gerar() {
        var bytes = new byte[TAMANHO_BYTES];
        RANDOM.nextBytes(bytes);
        return new TokenDecisao(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static String calcularHash(String valor) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    public static boolean confere(String informado, String hashArmazenado) {
        if (informado == null || informado.isBlank() || hashArmazenado == null) {
            return false;
        }
        return MessageDigest.isEqual(
            calcularHash(informado).getBytes(StandardCharsets.US_ASCII),
            hashArmazenado.getBytes(StandardCharsets.US_ASCII));
    }

    public String valor() {
        return valor;
    }

    public String hash() {
        return hash;
    }

    @Override
    public String toString() {
        return "TokenDecisao[***]";
    }
}
