package com.oficina.billing.infrastructure.gateway;

import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.infrastructure.gateway.mercadopago.MapeadorStatusMercadoPago;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

class MapeadorStatusMercadoPagoTest {

    @ParameterizedTest
    @CsvSource({
        "approved, APROVADO",
        "APPROVED, APROVADO",
        "rejected, RECUSADO",
        "cancelled, RECUSADO",
        "refunded, ESTORNADO",
        "charged_back, ESTORNADO",
        "pending, PENDENTE",
        "in_process, PENDENTE",
        "authorized, PENDENTE",
        "in_mediation, PENDENTE"
    })
    void mapeiaStatusDoMercadoPago(String status, StatusPagamento esperado) {
        assertThat(MapeadorStatusMercadoPago.mapear(status)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullSource
    void statusAusenteEPendente(String status) {
        assertThat(MapeadorStatusMercadoPago.mapear(status)).isEqualTo(StatusPagamento.PENDENTE);
    }
}
