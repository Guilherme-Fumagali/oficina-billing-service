package com.oficina.billing.support;

import com.oficina.billing.domain.entity.DadosNovoOrcamento;
import com.oficina.billing.domain.entity.DadosPagamento;
import com.oficina.billing.domain.entity.ItemOrcamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.domain.valueobject.TipoItem;
import com.oficina.billing.domain.valueobject.TokenDecisao;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Fixtures {

    public static final Instant AGORA = Instant.parse("2026-10-30T12:00:00Z");
    public static final Duration VALIDADE = Duration.ofHours(168);
    public static final String TOKEN = "token-de-decisao-do-cliente";

    private Fixtures() {}

    public static List<ItemOrcamento> itens() {
        return List.of(
            ItemOrcamento.novo(TipoItem.SERVICO, UUID.randomUUID(), "Troca de óleo", 1, new BigDecimal("120.00")),
            ItemOrcamento.novo(TipoItem.PECA, UUID.randomUUID(), "Filtro de óleo", 2, new BigDecimal("45.00")));
    }

    public static DadosNovoOrcamento dadosNovoOrcamento(UUID clienteId) {
        return new DadosNovoOrcamento(UUID.randomUUID(), UUID.randomUUID(), clienteId,
            "Maria Souza", "maria@example.com", "ABC1D23", "Corolla", itens());
    }

    public static Orcamento orcamento() {
        return orcamento(UUID.randomUUID());
    }

    public static Orcamento orcamento(UUID clienteId) {
        return Orcamento.criar(dadosNovoOrcamento(clienteId), TokenDecisao.calcularHash(TOKEN), AGORA, VALIDADE);
    }

    public static Orcamento orcamentoAprovado() {
        var orcamento = orcamento();
        orcamento.aprovar(CanalDecisao.CLIENTE, AGORA.plusSeconds(60));
        return orcamento;
    }

    public static Pagamento pagamentoPendente(Orcamento orcamento) {
        return Pagamento.criar(orcamento, "pref-123", "https://mp.example/checkout", AGORA.plus(Duration.ofHours(24)), AGORA);
    }

    public static Pagamento pagamentoAprovado(Orcamento orcamento, String mpPaymentId) {
        var pagamento = pagamentoPendente(orcamento);
        pagamento.aprovar(mpPaymentId, "visa", "accredited", AGORA.plusSeconds(120), "{}");
        return pagamento;
    }

    public static Pagamento pagamentoComStatus(Orcamento orcamento, StatusPagamento status) {
        return Pagamento.reconstituir(new DadosPagamento(UUID.randomUUID(), orcamento.getId(), orcamento.getOsId(),
            orcamento.getTotal(), status, "pref-123", "mp-" + status.name().toLowerCase(), "https://mp.example/checkout",
            null, null, AGORA.plus(Duration.ofHours(24)), AGORA, null, null, null, 3L));
    }
}
