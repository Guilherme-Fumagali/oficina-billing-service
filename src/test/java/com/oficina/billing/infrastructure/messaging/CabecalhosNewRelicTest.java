package com.oficina.billing.infrastructure.messaging;

import com.newrelic.api.agent.HeaderType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CabecalhosNewRelicTest {

    @Test
    void copiaApenasOsCabecalhosDeRastreamento() {
        Map<String, Object> origem = new HashMap<>();
        origem.put("traceparent", "00-abc-def-01");
        origem.put("Tracestate", "nr=1");
        origem.put("newrelic", "eyJ2Ijp9");
        origem.put("tipo", "GerarOrcamento");
        origem.put("vazio", null);

        var cabecalhos = CabecalhosNewRelic.de(origem);

        assertThat(cabecalhos.comoMapa()).containsOnlyKeys("traceparent", "tracestate", "newrelic");
        assertThat(cabecalhos.getHeader("TRACEPARENT")).isEqualTo("00-abc-def-01");
        assertThat(cabecalhos.getHeaders("tracestate")).containsExactly("nr=1");
        assertThat(cabecalhos.getHeaders("ausente")).isEmpty();
        assertThat(cabecalhos.containsHeader("newrelic")).isTrue();
        assertThat(cabecalhos.getHeaderType()).isEqualTo(HeaderType.MESSAGE);
    }

    @Test
    void recebeCabecalhosInseridosPeloAgente() {
        var cabecalhos = CabecalhosNewRelic.de(null);

        cabecalhos.setHeader("traceparent", "00-1");
        cabecalhos.addHeader("newrelic", "x");

        assertThat(cabecalhos.getHeaderNames()).containsExactlyInAnyOrder("traceparent", "newrelic");
    }
}
