package com.oficina.billing.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    @Query(value = """
        SELECT * FROM outbox
        WHERE publicada_em IS NULL
        ORDER BY criada_em
        LIMIT :limite
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxJpaEntity> reivindicarPendentes(@Param("limite") int limite);
}
