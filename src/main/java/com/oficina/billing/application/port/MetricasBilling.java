package com.oficina.billing.application.port;

import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.math.BigDecimal;
import java.util.UUID;

public interface MetricasBilling {

    void registrarOrcamentoGerado(UUID orcamentoId);

    void registrarAprovacao(UUID orcamentoId, CanalDecisao canal, BigDecimal valor);

    void registrarReprovacao(UUID orcamentoId, CanalDecisao canal);

    void registrarOrcamentoExpirado(UUID orcamentoId);

    void registrarResultadoPagamento(UUID pagamentoId, StatusPagamento status);

    void registrarIntegracaoMercadoPago(String operacao, String resultado);

    void registrarMensagemPublicada(String tipo);

    void registrarMensagemConsumida(String tipo, String resultado);

    void registrarFalha(String operacao, String referencia, Exception erro);
}
