package com.oficina.billing.infrastructure.persistence.adapter;

import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.infrastructure.persistence.mapper.DataHora;
import com.oficina.billing.infrastructure.persistence.mapper.OrcamentoMapper;
import com.oficina.billing.infrastructure.persistence.repository.OrcamentoJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrcamentoRepositoryAdapter implements OrcamentoRepository {

    private final OrcamentoJpaRepository jpa;

    public OrcamentoRepositoryAdapter(OrcamentoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Orcamento salvar(Orcamento orcamento) {
        return OrcamentoMapper.paraDominio(jpa.saveAndFlush(OrcamentoMapper.paraEntidade(orcamento)));
    }

    @Override
    public Optional<Orcamento> buscarPorId(UUID id) {
        return jpa.findComItensById(id).map(OrcamentoMapper::paraDominio);
    }

    @Override
    public Optional<Orcamento> buscarPorOsId(UUID osId) {
        return jpa.findByOsId(osId).map(OrcamentoMapper::paraDominio);
    }

    @Override
    public List<Orcamento> reivindicarVencidos(Instant agora, int limite) {
        return jpa.reivindicarVencidos(DataHora.utc(agora), limite).stream()
            .map(OrcamentoMapper::paraDominio)
            .toList();
    }
}
