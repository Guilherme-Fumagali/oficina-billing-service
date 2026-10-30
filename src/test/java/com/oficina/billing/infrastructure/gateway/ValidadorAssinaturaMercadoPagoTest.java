package com.oficina.billing.infrastructure.gateway;

import com.oficina.billing.infrastructure.gateway.mercadopago.PropriedadesMercadoPago;
import com.oficina.billing.infrastructure.gateway.mercadopago.ResultadoAssinatura;
import com.oficina.billing.infrastructure.gateway.mercadopago.ValidadorAssinaturaMercadoPago;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorAssinaturaMercadoPagoTest {

    private static final String SEGREDO = "segredo-webhook-de-teste";
    private static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
    private static final String TS = "1742505638683";
    private static final String ASSINATURA_CONHECIDA = "1f84d84f04b700c79a664a336c36f288d037d51ef5205fca65351ea4c94fa0e3";

    private final ValidadorAssinaturaMercadoPago validador = validador(SEGREDO);

    @Test
    void aceitaAssinaturaDoVetorConhecido() {
        var cabecalho = "ts=%s,v1=%s".formatted(TS, ASSINATURA_CONHECIDA);

        assertThat(validador.verificar(cabecalho, REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.VALIDA);
    }

    @Test
    void aceitaEspacosEMaiusculasNoCabecalho() {
        var cabecalho = " ts = %s , v1 = %s ".formatted(TS, ASSINATURA_CONHECIDA.toUpperCase());

        assertThat(validador.verificar(cabecalho, REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.VALIDA);
    }

    @Test
    void dataIdAlfanumericoEConvertidoParaMinusculasEPartesAusentesSaemDoManifesto() {
        var cabecalho = "ts=%s,v1=%s".formatted(TS, "c00a25d61226b4899347c6d3d3c0916811a8d1cce5e62c1c09ecadeb7ed91ce4");

        assertThat(validador.verificar(cabecalho, null, "ABC123DEF")).isEqualTo(ResultadoAssinatura.VALIDA);
    }

    @Test
    void recusaAssinaturaAlteradaOuDeOutroPagamento() {
        var cabecalho = "ts=%s,v1=%s".formatted(TS, ASSINATURA_CONHECIDA);

        assertThat(validador.verificar(cabecalho, REQUEST_ID, "123457")).isEqualTo(ResultadoAssinatura.INVALIDA);
        assertThat(validador.verificar(cabecalho, "outro-request", "123456")).isEqualTo(ResultadoAssinatura.INVALIDA);
        assertThat(validador.verificar("ts=%s,v1=00".formatted(TS), REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.INVALIDA);
    }

    @Test
    void cabecalhoIncompletoOuAusente() {
        assertThat(validador.verificar(null, REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.AUSENTE);
        assertThat(validador.verificar(" ", REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.AUSENTE);
        assertThat(validador.verificar("v1=" + ASSINATURA_CONHECIDA, REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.INVALIDA);
        assertThat(validador.verificar("ts=" + TS, REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.INVALIDA);
        assertThat(validador.verificar("lixo", REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.INVALIDA);
    }

    @Test
    void semSegredoConfiguradoNenhumaAssinaturaEValida() {
        var semSegredo = validador(null);
        var cabecalho = "ts=%s,v1=%s".formatted(TS, ASSINATURA_CONHECIDA);

        assertThat(semSegredo.verificar(cabecalho, REQUEST_ID, "123456")).isEqualTo(ResultadoAssinatura.INVALIDA);
    }

    @Test
    void rotuloDaMetrica() {
        assertThat(ResultadoAssinatura.VALIDA.rotulo()).isEqualTo("valida");
        assertThat(ResultadoAssinatura.AUSENTE.rotulo()).isEqualTo("ausente");
    }

    private static ValidadorAssinaturaMercadoPago validador(String segredo) {
        return new ValidadorAssinaturaMercadoPago(new PropriedadesMercadoPago("http://mp", "token", segredo, true, false,
            "http://retorno", null, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }
}
