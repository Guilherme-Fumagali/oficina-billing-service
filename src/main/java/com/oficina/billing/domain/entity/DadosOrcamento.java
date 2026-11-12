package com.oficina.billing.domain.entity;

import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DadosOrcamento(
    UUID id,
    UUID osId,
    UUID sagaId,
    UUID clienteId,
    String clienteNome,
    String clienteEmail,
    String veiculoPlaca,
    String veiculoModelo,
    List<ItemOrcamento> itens,
    BigDecimal total,
    StatusOrcamento status,
    Instant criadoEm,
    Instant validoAte,
    String tokenDecisaoHash,
    CanalDecisao canalDecisao,
    Instant aprovadoEm,
    Instant reprovadoEm,
    Instant expiradoEm,
    Instant canceladoEm,
    String motivoCancelamento,
    Long versao
) {}
