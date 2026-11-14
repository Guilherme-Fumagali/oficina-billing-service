package com.oficina.billing.domain;

import com.oficina.billing.domain.entity.ItemOrcamento;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.valueobject.TipoItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemOrcamentoTest {

    private static final UUID REFERENCIA = UUID.randomUUID();

    @Test
    void subtotalEPrecoUnitarioVezesQuantidadeComDuasCasas() {
        var item = ItemOrcamento.novo(TipoItem.PECA, REFERENCIA, "  Pastilha de freio ", 3, new BigDecimal("33.335"));

        assertThat(item.getPrecoUnitario()).isEqualTo(new BigDecimal("33.34"));
        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal("100.02"));
        assertThat(item.getDescricao()).isEqualTo("Pastilha de freio");
        assertThat(item.getId()).isNotNull();
        assertThat(item.getTipo()).isEqualTo(TipoItem.PECA);
        assertThat(item.getReferenciaId()).isEqualTo(REFERENCIA);
    }

    @Test
    void recusaItemSemTipoReferenciaOuDescricao() {
        var preco = BigDecimal.TEN;
        assertThatThrownBy(() -> ItemOrcamento.novo(null, REFERENCIA, "x", 1, preco)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, null, "x", 1, preco)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, " ", 1, preco)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, null, 1, preco)).isInstanceOf(DomainException.class);
    }

    @Test
    void recusaQuantidadeMenorQueUm() {
        var preco = BigDecimal.TEN;
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, "x", 0, preco))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("quantidade");
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, "x", null, preco))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void recusaPrecoNaoPositivo() {
        var zero = BigDecimal.ZERO;
        var negativo = new BigDecimal("-1.00");
        var arredondaParaZero = new BigDecimal("0.004");
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, "x", 1, zero)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, "x", 1, negativo)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, "x", 1, arredondaParaZero)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemOrcamento.novo(TipoItem.SERVICO, REFERENCIA, "x", 1, null)).isInstanceOf(DomainException.class);
    }

    @Test
    void reconstituirPreservaOsValoresPersistidos() {
        var id = UUID.randomUUID();
        var item = ItemOrcamento.reconstituir(id, TipoItem.SERVICO, REFERENCIA, "Alinhamento", 2,
            new BigDecimal("80.00"), new BigDecimal("160.00"));

        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.getQuantidade()).isEqualTo(2);
        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal("160.00"));
    }
}
