package com.oficina.billing.infrastructure.persistence.entity;

import com.oficina.billing.domain.valueobject.StatusPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamentos")
@Getter
@Setter
@NoArgsConstructor
public class PagamentoJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "orcamento_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID orcamentoId;

    @Column(name = "os_id", nullable = false, columnDefinition = "uuid")
    private UUID osId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "mp_preference_id", length = 100)
    private String mpPreferenceId;

    @Column(name = "mp_payment_id", unique = true, length = 40)
    private String mpPaymentId;

    @Column(name = "link_pagamento", nullable = false, length = 500)
    private String linkPagamento;

    @Column(length = 40)
    private String meio;

    @Column(name = "status_detalhe", length = 80)
    private String statusDetalhe;

    @Column(nullable = false)
    private LocalDateTime prazo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @Column(name = "estornado_em")
    private LocalDateTime estornadoEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_mp", columnDefinition = "jsonb")
    private String payloadMp;

    @Version
    @Column(nullable = false)
    private Long versao;
}
