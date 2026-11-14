package com.oficina.billing.infrastructure.persistence.repository;

import com.oficina.billing.infrastructure.persistence.entity.OrcamentoJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoJpaRepository extends JpaRepository<OrcamentoJpaEntity, UUID> {

    @EntityGraph(attributePaths = "itens")
    Optional<OrcamentoJpaEntity> findComItensById(UUID id);

    @EntityGraph(attributePaths = "itens")
    Optional<OrcamentoJpaEntity> findByOsId(UUID osId);

    @Query(value = """
        SELECT * FROM orcamentos
        WHERE status = 'AGUARDANDO_APROVACAO' AND valido_ate <= :agora
        ORDER BY valido_ate
        LIMIT :limite
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OrcamentoJpaEntity> reivindicarVencidos(@Param("agora") LocalDateTime agora, @Param("limite") int limite);
}
