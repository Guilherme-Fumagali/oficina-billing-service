package com.oficina.billing.infrastructure.persistence.entity;

import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class OrcamentoJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "os_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID osId;

    @Column(name = "saga_id", nullable = false, columnDefinition = "uuid")
    private UUID sagaId;

    @Column(name = "cliente_id", nullable = false, columnDefinition = "uuid")
    private UUID clienteId;

    @Column(name = "cliente_nome", nullable = false, length = 160)
    private String clienteNome;

    @Column(name = "cliente_email", nullable = false, length = 160)
    private String clienteEmail;

    @Column(name = "veiculo_placa", nullable = false, length = 10)
    private String veiculoPlaca;

    @Column(name = "veiculo_modelo", nullable = false, length = 100)
    private String veiculoModelo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusOrcamento status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "valido_ate", nullable = false)
    private LocalDateTime validoAte;

    @Column(name = "token_decisao_hash", nullable = false, length = 64)
    private String tokenDecisaoHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_decisao", length = 10)
    private CanalDecisao canalDecisao;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @Column(name = "reprovado_em")
    private LocalDateTime reprovadoEm;

    @Column(name = "expirado_em")
    private LocalDateTime expiradoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "motivo_cancelamento", length = 200)
    private String motivoCancelamento;

    @Version
    @Column(nullable = false)
    private Long versao;

    @OneToMany(mappedBy = "orcamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("descricao")
    private List<ItemOrcamentoJpaEntity> itens = new ArrayList<>();
}
