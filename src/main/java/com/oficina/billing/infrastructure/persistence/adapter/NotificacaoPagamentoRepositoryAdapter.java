package com.oficina.billing.infrastructure.persistence.adapter;

import com.oficina.billing.domain.entity.NotificacaoPagamento;
import com.oficina.billing.domain.repository.NotificacaoPagamentoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

@Component
public class NotificacaoPagamentoRepositoryAdapter implements NotificacaoPagamentoRepository {

    private final JdbcTemplate jdbc;

    public NotificacaoPagamentoRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(NotificacaoPagamento notificacao) {
        jdbc.update("""
            INSERT INTO notificacoes_pagamento (id, request_id, data_id, tipo, assinatura_valida, recebida_em)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            notificacao.id(), limitar(notificacao.requestId(), 100), limitar(notificacao.dataId(), 100),
            limitar(notificacao.tipo(), 60), notificacao.assinaturaValida(), utc(notificacao.recebidaEm()));
    }

    private static String limitar(String valor, int tamanho) {
        return valor == null || valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }
}
