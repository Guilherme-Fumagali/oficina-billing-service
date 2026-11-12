package com.oficina.billing.domain.entity;

import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.valueobject.TipoItem;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Getter
public class ItemOrcamento {

    private final UUID id;
    private final TipoItem tipo;
    private final UUID referenciaId;
    private final String descricao;
    private final int quantidade;
    private final BigDecimal precoUnitario;
    private final BigDecimal subtotal;

    private ItemOrcamento(UUID id, TipoItem tipo, UUID referenciaId, String descricao,
                          int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
        this.id = id;
        this.tipo = tipo;
        this.referenciaId = referenciaId;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.subtotal = subtotal;
    }

    public static ItemOrcamento novo(TipoItem tipo, UUID referenciaId, String descricao,
                                     Integer quantidade, BigDecimal precoUnitario) {
        if (tipo == null) {
            throw new DomainException("O tipo do item é obrigatório.");
        }
        if (referenciaId == null) {
            throw new DomainException("A referência do item é obrigatória.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new DomainException("A descrição do item é obrigatória.");
        }
        if (quantidade == null || quantidade < 1) {
            throw new DomainException("A quantidade do item deve ser de pelo menos 1.");
        }
        var preco = precoUnitario == null ? null : precoUnitario.setScale(2, RoundingMode.HALF_EVEN);
        if (preco == null || preco.signum() <= 0) {
            throw new DomainException("O preço unitário do item deve ser maior que zero.");
        }
        var subtotal = preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_EVEN);
        return new ItemOrcamento(UUID.randomUUID(), tipo, referenciaId, descricao.strip(), quantidade, preco, subtotal);
    }

    public static ItemOrcamento reconstituir(UUID id, TipoItem tipo, UUID referenciaId, String descricao,
                                             int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
        return new ItemOrcamento(id, tipo, referenciaId, descricao, quantidade, precoUnitario, subtotal);
    }
}
