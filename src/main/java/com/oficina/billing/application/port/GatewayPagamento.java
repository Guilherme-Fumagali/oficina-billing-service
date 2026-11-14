package com.oficina.billing.application.port;

import java.util.Optional;
import java.util.UUID;

public interface GatewayPagamento {

    CobrancaCriada criarCobranca(SolicitacaoCobranca solicitacao);

    PagamentoGateway consultarPagamento(String pagamentoGatewayId);

    Optional<PagamentoGateway> buscarPorOrcamento(UUID orcamentoId);

    void estornar(String pagamentoGatewayId, String chaveIdempotencia);
}
