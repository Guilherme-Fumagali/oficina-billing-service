package com.oficina.billing.domain.entity;

import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.exception.TransicaoInvalidaException;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class Pagamento {

    public static final String MOTIVO_EXPIRADO = "EXPIRADO";

    private final UUID id;
    private final UUID orcamentoId;
    private final UUID osId;
    private final BigDecimal valor;
    private StatusPagamento status;
    private final String mpPreferenceId;
    private String mpPaymentId;
    private final String linkPagamento;
    private String meio;
    private String statusDetalhe;
    private final Instant prazo;
    private final Instant criadoEm;
    private Instant aprovadoEm;
    private Instant estornadoEm;
    private String payloadGateway;
    private final Long versao;

    private Pagamento(DadosPagamento dados) {
        this.id = dados.id();
        this.orcamentoId = dados.orcamentoId();
        this.osId = dados.osId();
        this.valor = dados.valor();
        this.status = dados.status();
        this.mpPreferenceId = dados.mpPreferenceId();
        this.mpPaymentId = dados.mpPaymentId();
        this.linkPagamento = dados.linkPagamento();
        this.meio = dados.meio();
        this.statusDetalhe = dados.statusDetalhe();
        this.prazo = dados.prazo();
        this.criadoEm = dados.criadoEm();
        this.aprovadoEm = dados.aprovadoEm();
        this.estornadoEm = dados.estornadoEm();
        this.payloadGateway = dados.payloadGateway();
        this.versao = dados.versao();
    }

    public static Pagamento criar(Orcamento orcamento, String mpPreferenceId, String linkPagamento,
                                  Instant prazo, Instant agora) {
        if (linkPagamento == null || linkPagamento.isBlank()) {
            throw new DomainException("O link de pagamento é obrigatório.");
        }
        if (!prazo.isAfter(agora)) {
            throw new DomainException("O prazo de pagamento deve ser posterior à criação da cobrança.");
        }
        return new Pagamento(new DadosPagamento(
            UUID.randomUUID(), orcamento.getId(), orcamento.getOsId(), orcamento.getTotal(),
            StatusPagamento.PENDENTE, mpPreferenceId, null, linkPagamento, null, null,
            prazo, agora, null, null, null, null));
    }

    public static Pagamento reconstituir(DadosPagamento dados) {
        return new Pagamento(dados);
    }

    public boolean isPendente() {
        return status == StatusPagamento.PENDENTE;
    }

    public boolean isAprovado() {
        return status == StatusPagamento.APROVADO;
    }

    public boolean correspondeAo(String pagamentoGatewayId) {
        return mpPaymentId != null && mpPaymentId.equals(pagamentoGatewayId);
    }

    public boolean vencidoEm(Instant instante) {
        return !instante.isBefore(prazo);
    }

    public void aprovar(String pagamentoGatewayId, String meioPagamento, String detalhe,
                        Instant aprovacao, String payload) {
        exigirPendente("aprovar");
        this.status = StatusPagamento.APROVADO;
        this.mpPaymentId = pagamentoGatewayId;
        this.meio = meioPagamento;
        this.statusDetalhe = detalhe;
        this.aprovadoEm = aprovacao;
        this.payloadGateway = payload;
    }

    public void recusar(String pagamentoGatewayId, String detalhe, String payload) {
        exigirPendente("recusar");
        this.status = StatusPagamento.RECUSADO;
        this.mpPaymentId = pagamentoGatewayId;
        this.statusDetalhe = detalhe;
        this.payloadGateway = payload;
    }

    public void registrarTentativaRecusada(String detalhe, String payload) {
        exigirPendente("registrar tentativa recusada");
        this.statusDetalhe = detalhe;
        this.payloadGateway = payload;
    }

    public void expirar(Instant agora) {
        exigirPendente("expirar");
        if (!vencidoEm(agora)) {
            throw new TransicaoInvalidaException("O pagamento %s ainda está no prazo.".formatted(id));
        }
        this.status = StatusPagamento.EXPIRADO;
        this.statusDetalhe = MOTIVO_EXPIRADO;
    }

    public void estornar(Instant agora) {
        if (status != StatusPagamento.APROVADO) {
            throw new TransicaoInvalidaException(
                "Somente pagamento aprovado pode ser estornado (pagamento %s está %s).".formatted(id, status));
        }
        this.status = StatusPagamento.ESTORNADO;
        this.estornadoEm = agora;
    }

    private void exigirPendente(String operacao) {
        if (status != StatusPagamento.PENDENTE) {
            throw new TransicaoInvalidaException(
                "Não é possível %s o pagamento %s no status %s.".formatted(operacao, id, status));
        }
    }
}
