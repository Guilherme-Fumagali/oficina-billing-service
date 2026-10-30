package com.oficina.billing.infrastructure.persistence.mapper;

import com.oficina.billing.domain.entity.DadosPagamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.infrastructure.persistence.entity.PagamentoJpaEntity;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.instante;
import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

public final class PagamentoMapper {

    private PagamentoMapper() {}

    public static PagamentoJpaEntity paraEntidade(Pagamento pagamento) {
        var entidade = new PagamentoJpaEntity();
        entidade.setId(pagamento.getId());
        entidade.setOrcamentoId(pagamento.getOrcamentoId());
        entidade.setOsId(pagamento.getOsId());
        entidade.setValor(pagamento.getValor());
        entidade.setStatus(pagamento.getStatus());
        entidade.setMpPreferenceId(pagamento.getMpPreferenceId());
        entidade.setMpPaymentId(pagamento.getMpPaymentId());
        entidade.setLinkPagamento(pagamento.getLinkPagamento());
        entidade.setMeio(pagamento.getMeio());
        entidade.setStatusDetalhe(pagamento.getStatusDetalhe());
        entidade.setPrazo(utc(pagamento.getPrazo()));
        entidade.setCriadoEm(utc(pagamento.getCriadoEm()));
        entidade.setAprovadoEm(utc(pagamento.getAprovadoEm()));
        entidade.setEstornadoEm(utc(pagamento.getEstornadoEm()));
        entidade.setPayloadMp(pagamento.getPayloadGateway());
        entidade.setVersao(pagamento.getVersao());
        return entidade;
    }

    public static Pagamento paraDominio(PagamentoJpaEntity entidade) {
        return Pagamento.reconstituir(new DadosPagamento(
            entidade.getId(), entidade.getOrcamentoId(), entidade.getOsId(), entidade.getValor(),
            entidade.getStatus(), entidade.getMpPreferenceId(), entidade.getMpPaymentId(),
            entidade.getLinkPagamento(), entidade.getMeio(), entidade.getStatusDetalhe(),
            instante(entidade.getPrazo()), instante(entidade.getCriadoEm()),
            instante(entidade.getAprovadoEm()), instante(entidade.getEstornadoEm()),
            entidade.getPayloadMp(), entidade.getVersao()));
    }
}
