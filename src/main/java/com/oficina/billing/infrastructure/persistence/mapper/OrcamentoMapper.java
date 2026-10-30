package com.oficina.billing.infrastructure.persistence.mapper;

import com.oficina.billing.domain.entity.DadosOrcamento;
import com.oficina.billing.domain.entity.ItemOrcamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.infrastructure.persistence.entity.ItemOrcamentoJpaEntity;
import com.oficina.billing.infrastructure.persistence.entity.OrcamentoJpaEntity;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.instante;
import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

public final class OrcamentoMapper {

    private OrcamentoMapper() {}

    public static OrcamentoJpaEntity paraEntidade(Orcamento orcamento) {
        var entidade = new OrcamentoJpaEntity();
        entidade.setId(orcamento.getId());
        entidade.setOsId(orcamento.getOsId());
        entidade.setSagaId(orcamento.getSagaId());
        entidade.setClienteId(orcamento.getClienteId());
        entidade.setClienteNome(orcamento.getClienteNome());
        entidade.setClienteEmail(orcamento.getClienteEmail());
        entidade.setVeiculoPlaca(orcamento.getVeiculoPlaca());
        entidade.setVeiculoModelo(orcamento.getVeiculoModelo());
        entidade.setTotal(orcamento.getTotal());
        entidade.setStatus(orcamento.getStatus());
        entidade.setCriadoEm(utc(orcamento.getCriadoEm()));
        entidade.setValidoAte(utc(orcamento.getValidoAte()));
        entidade.setTokenDecisaoHash(orcamento.getTokenDecisaoHash());
        entidade.setCanalDecisao(orcamento.getCanalDecisao());
        entidade.setAprovadoEm(utc(orcamento.getAprovadoEm()));
        entidade.setReprovadoEm(utc(orcamento.getReprovadoEm()));
        entidade.setExpiradoEm(utc(orcamento.getExpiradoEm()));
        entidade.setCanceladoEm(utc(orcamento.getCanceladoEm()));
        entidade.setMotivoCancelamento(orcamento.getMotivoCancelamento());
        entidade.setVersao(orcamento.getVersao());
        orcamento.getItens().forEach(item -> entidade.getItens().add(paraEntidade(item, entidade)));
        return entidade;
    }

    public static Orcamento paraDominio(OrcamentoJpaEntity entidade) {
        var itens = entidade.getItens().stream().map(OrcamentoMapper::paraDominio).toList();
        return Orcamento.reconstituir(new DadosOrcamento(
            entidade.getId(), entidade.getOsId(), entidade.getSagaId(), entidade.getClienteId(),
            entidade.getClienteNome(), entidade.getClienteEmail(),
            entidade.getVeiculoPlaca(), entidade.getVeiculoModelo(),
            itens, entidade.getTotal(), entidade.getStatus(),
            instante(entidade.getCriadoEm()), instante(entidade.getValidoAte()), entidade.getTokenDecisaoHash(),
            entidade.getCanalDecisao(), instante(entidade.getAprovadoEm()), instante(entidade.getReprovadoEm()),
            instante(entidade.getExpiradoEm()), instante(entidade.getCanceladoEm()),
            entidade.getMotivoCancelamento(), entidade.getVersao()));
    }

    private static ItemOrcamentoJpaEntity paraEntidade(ItemOrcamento item, OrcamentoJpaEntity orcamento) {
        var entidade = new ItemOrcamentoJpaEntity();
        entidade.setId(item.getId());
        entidade.setOrcamento(orcamento);
        entidade.setTipo(item.getTipo());
        entidade.setReferenciaId(item.getReferenciaId());
        entidade.setDescricao(item.getDescricao());
        entidade.setQuantidade(item.getQuantidade());
        entidade.setPrecoUnitario(item.getPrecoUnitario());
        entidade.setSubtotal(item.getSubtotal());
        return entidade;
    }

    private static ItemOrcamento paraDominio(ItemOrcamentoJpaEntity entidade) {
        return ItemOrcamento.reconstituir(entidade.getId(), entidade.getTipo(), entidade.getReferenciaId(),
            entidade.getDescricao(), entidade.getQuantidade(), entidade.getPrecoUnitario(), entidade.getSubtotal());
    }
}
