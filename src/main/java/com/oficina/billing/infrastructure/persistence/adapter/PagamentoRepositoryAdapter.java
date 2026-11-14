package com.oficina.billing.infrastructure.persistence.adapter;

import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.infrastructure.persistence.mapper.PagamentoMapper;
import com.oficina.billing.infrastructure.persistence.repository.PagamentoJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

@Component
public class PagamentoRepositoryAdapter implements PagamentoRepository {

    private static final String RESERVAR_CRIADOS_ATE = """
        UPDATE pagamentos SET reservado_ate = ?
        WHERE id IN (
            SELECT id FROM pagamentos
            WHERE status = 'PENDENTE' AND criado_em <= ? AND (reservado_ate IS NULL OR reservado_ate < ?)
            ORDER BY criado_em
            LIMIT ?
            FOR UPDATE SKIP LOCKED)
        RETURNING id
        """;

    private static final String RESERVAR_VENCIDOS = """
        UPDATE pagamentos SET reservado_ate = ?
        WHERE id IN (
            SELECT id FROM pagamentos
            WHERE status = 'PENDENTE' AND prazo <= ? AND (reservado_ate IS NULL OR reservado_ate < ?)
            ORDER BY prazo
            LIMIT ?
            FOR UPDATE SKIP LOCKED)
        RETURNING id
        """;

    private final PagamentoJpaRepository jpa;
    private final JdbcTemplate jdbc;

    public PagamentoRepositoryAdapter(PagamentoJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        return PagamentoMapper.paraDominio(jpa.saveAndFlush(PagamentoMapper.paraEntidade(pagamento)));
    }

    @Override
    public Optional<Pagamento> buscarPorId(UUID id) {
        return jpa.findById(id).map(PagamentoMapper::paraDominio);
    }

    @Override
    public Optional<Pagamento> buscarPorOrcamentoId(UUID orcamentoId) {
        return jpa.findByOrcamentoId(orcamentoId).map(PagamentoMapper::paraDominio);
    }

    @Override
    public Optional<Pagamento> buscarPorOsId(UUID osId) {
        return jpa.findByOsId(osId).map(PagamentoMapper::paraDominio);
    }

    @Override
    public List<UUID> reservarPendentesCriadosAte(Instant criadoAte, Instant agora, Instant reservaAte, int limite) {
        return jdbc.queryForList(RESERVAR_CRIADOS_ATE, UUID.class, utc(reservaAte), utc(criadoAte), utc(agora), limite);
    }

    @Override
    public List<UUID> reservarPendentesVencidos(Instant agora, Instant reservaAte, int limite) {
        return jdbc.queryForList(RESERVAR_VENCIDOS, UUID.class, utc(reservaAte), utc(agora), utc(agora), limite);
    }
}
