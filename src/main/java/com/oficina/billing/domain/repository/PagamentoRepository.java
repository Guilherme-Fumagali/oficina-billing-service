package com.oficina.billing.domain.repository;

import com.oficina.billing.domain.entity.Pagamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository {

    Pagamento salvar(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(UUID id);

    Optional<Pagamento> buscarPorOrcamentoId(UUID orcamentoId);

    Optional<Pagamento> buscarPorOsId(UUID osId);

    List<UUID> reservarPendentesCriadosAte(Instant criadoAte, Instant agora, Instant reservaAte, int limite);

    List<UUID> reservarPendentesVencidos(Instant agora, Instant reservaAte, int limite);
}
