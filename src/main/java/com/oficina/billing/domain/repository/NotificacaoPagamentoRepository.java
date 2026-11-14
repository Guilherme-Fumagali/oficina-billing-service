package com.oficina.billing.domain.repository;

import com.oficina.billing.domain.entity.NotificacaoPagamento;

public interface NotificacaoPagamentoRepository {

    void registrar(NotificacaoPagamento notificacao);
}
