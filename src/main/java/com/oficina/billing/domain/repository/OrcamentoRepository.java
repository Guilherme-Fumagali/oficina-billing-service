package com.oficina.billing.domain.repository;

import com.oficina.billing.domain.entity.Orcamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository {

    Orcamento salvar(Orcamento orcamento);

    Optional<Orcamento> buscarPorId(UUID id);

    Optional<Orcamento> buscarPorOsId(UUID osId);

    List<Orcamento> reivindicarVencidos(Instant agora, int limite);
}
