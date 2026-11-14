package com.oficina.billing.infrastructure.persistence.repository;

import com.oficina.billing.infrastructure.persistence.entity.PagamentoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PagamentoJpaRepository extends JpaRepository<PagamentoJpaEntity, UUID> {

    Optional<PagamentoJpaEntity> findByOrcamentoId(UUID orcamentoId);

    Optional<PagamentoJpaEntity> findByOsId(UUID osId);
}
