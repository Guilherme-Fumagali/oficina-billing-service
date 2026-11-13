package com.oficina.billing.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("app.mensageria")
public record PropriedadesMensageria(Map<String, String> filas, String dlq, Relay relay) {

    public static final String FILA_OS_EVENTOS = "os-eventos";

    public record Relay(int lote) {}

    public String nomeDaFila(String chave) {
        var nome = filas.get(chave);
        if (nome == null || nome.isBlank()) {
            throw new IllegalStateException("Fila não configurada: app.mensageria.filas." + chave);
        }
        return nome;
    }
}
