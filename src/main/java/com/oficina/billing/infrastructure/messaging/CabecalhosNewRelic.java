package com.oficina.billing.infrastructure.messaging;

import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.Headers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CabecalhosNewRelic implements Headers {

    static final Set<String> NOMES = Set.of("newrelic", "traceparent", "tracestate");

    private final Map<String, String> valores = new LinkedHashMap<>();

    public static CabecalhosNewRelic de(Map<String, ?> origem) {
        var cabecalhos = new CabecalhosNewRelic();
        if (origem != null) {
            origem.forEach((nome, valor) -> {
                if (valor != null && NOMES.contains(nome.toLowerCase())) {
                    cabecalhos.valores.put(nome.toLowerCase(), valor.toString());
                }
            });
        }
        return cabecalhos;
    }

    public Map<String, String> comoMapa() {
        return Map.copyOf(valores);
    }

    @Override
    public HeaderType getHeaderType() {
        return HeaderType.MESSAGE;
    }

    @Override
    public String getHeader(String nome) {
        return valores.get(nome.toLowerCase());
    }

    @Override
    public Collection<String> getHeaders(String nome) {
        var valor = getHeader(nome);
        return valor == null ? List.of() : List.of(valor);
    }

    @Override
    public void setHeader(String nome, String valor) {
        valores.put(nome.toLowerCase(), valor);
    }

    @Override
    public void addHeader(String nome, String valor) {
        setHeader(nome, valor);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return List.copyOf(valores.keySet());
    }

    @Override
    public boolean containsHeader(String nome) {
        return valores.containsKey(nome.toLowerCase());
    }
}
