package com.oficina.billing.infrastructure.gateway.mercadopago;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ValidadorAssinaturaMercadoPago {

    private static final String ALGORITMO = "HmacSHA256";
    private static final Pattern ALFANUMERICO = Pattern.compile("[A-Za-z0-9]+");

    private final byte[] segredo;

    public ValidadorAssinaturaMercadoPago(PropriedadesMercadoPago propriedades) {
        var configurado = propriedades.webhookSecret();
        this.segredo = configurado == null ? new byte[0] : configurado.getBytes(StandardCharsets.UTF_8);
    }

    public ResultadoAssinatura verificar(String cabecalhoAssinatura, String requestId, String dataId) {
        if (cabecalhoAssinatura == null || cabecalhoAssinatura.isBlank()) {
            return ResultadoAssinatura.AUSENTE;
        }
        String ts = null;
        String v1 = null;
        for (var parte : cabecalhoAssinatura.split(",")) {
            var chaveValor = parte.split("=", 2);
            if (chaveValor.length == 2) {
                var chave = chaveValor[0].strip();
                if ("ts".equals(chave)) {
                    ts = chaveValor[1].strip();
                } else if ("v1".equals(chave)) {
                    v1 = chaveValor[1].strip();
                }
            }
        }
        if (ts == null || v1 == null || segredo.length == 0) {
            return ResultadoAssinatura.INVALIDA;
        }
        var esperado = assinar(manifesto(dataId, requestId, ts));
        var recebido = v1.toLowerCase(Locale.ROOT);
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII), recebido.getBytes(StandardCharsets.US_ASCII))
            ? ResultadoAssinatura.VALIDA
            : ResultadoAssinatura.INVALIDA;
    }

    static String manifesto(String dataId, String requestId, String ts) {
        var manifesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifesto.append("id:").append(normalizarDataId(dataId)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifesto.append("request-id:").append(requestId).append(';');
        }
        return manifesto.append("ts:").append(ts).append(';').toString();
    }

    static String normalizarDataId(String dataId) {
        return ALFANUMERICO.matcher(dataId).matches() ? dataId.toLowerCase(Locale.ROOT) : dataId;
    }

    private String assinar(String manifesto) {
        try {
            var mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo, ALGORITMO));
            return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível na JVM.", e);
        }
    }
}
