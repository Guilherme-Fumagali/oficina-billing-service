package com.oficina.billing.domain;

import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.exception.TransicaoInvalidaException;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.orcamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoPendente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoTest {

    @Test
    void criarGeraCobrancaPendenteComOValorDoOrcamento() {
        var orcamento = orcamentoAprovado();

        var pagamento = pagamentoPendente(orcamento);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getValor()).isEqualByComparingTo(orcamento.getTotal());
        assertThat(pagamento.getOrcamentoId()).isEqualTo(orcamento.getId());
        assertThat(pagamento.getOsId()).isEqualTo(orcamento.getOsId());
        assertThat(pagamento.getMpPreferenceId()).isEqualTo("pref-123");
        assertThat(pagamento.getCriadoEm()).isEqualTo(AGORA);
        assertThat(pagamento.isPendente()).isTrue();
        assertThat(pagamento.isAprovado()).isFalse();
    }

    @Test
    void criarExigeLinkEPrazoFuturo() {
        var orcamento = orcamentoAprovado();
        var prazo = AGORA.plusSeconds(60);

        assertThatThrownBy(() -> Pagamento.criar(orcamento, "pref", " ", prazo, AGORA)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Pagamento.criar(orcamento, "pref", null, prazo, AGORA)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Pagamento.criar(orcamento, "pref", "https://link", AGORA, AGORA)).isInstanceOf(DomainException.class);
    }

    @Test
    void aprovarRegistraODadoDoGateway() {
        var pagamento = pagamentoPendente(orcamentoAprovado());

        pagamento.aprovar("987", "pix", "accredited", AGORA, "{\"id\":987}");

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.getMpPaymentId()).isEqualTo("987");
        assertThat(pagamento.getMeio()).isEqualTo("pix");
        assertThat(pagamento.getStatusDetalhe()).isEqualTo("accredited");
        assertThat(pagamento.getAprovadoEm()).isEqualTo(AGORA);
        assertThat(pagamento.getPayloadGateway()).contains("987");
        assertThat(pagamento.correspondeAo("987")).isTrue();
        assertThat(pagamento.correspondeAo("988")).isFalse();
    }

    @Test
    void recusarEncerraOPagamento() {
        var pagamento = pagamentoPendente(orcamentoAprovado());

        pagamento.recusar("555", "cc_rejected_other_reason", "{}");

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(pagamento.getMpPaymentId()).isEqualTo("555");
        assertThatThrownBy(() -> pagamento.aprovar("556", "visa", "accredited", AGORA, "{}"))
            .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void tentativaRecusadaMantemPagamentoPendente() {
        var pagamento = pagamentoPendente(orcamentoAprovado());

        pagamento.registrarTentativaRecusada("cc_rejected_insufficient_amount", "{}");

        assertThat(pagamento.isPendente()).isTrue();
        assertThat(pagamento.getStatusDetalhe()).isEqualTo("cc_rejected_insufficient_amount");
        assertThat(pagamento.getMpPaymentId()).isNull();
    }

    @Test
    void expirarSomenteAposOPrazo() {
        var pagamento = pagamentoPendente(orcamentoAprovado());

        assertThat(pagamento.vencidoEm(AGORA)).isFalse();
        assertThatThrownBy(() -> pagamento.expirar(AGORA)).isInstanceOf(TransicaoInvalidaException.class);

        pagamento.expirar(AGORA.plus(Duration.ofHours(24)));

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(pagamento.getStatusDetalhe()).isEqualTo(Pagamento.MOTIVO_EXPIRADO);
    }

    @Test
    void estornarExigePagamentoAprovado() {
        var pendente = pagamentoPendente(orcamentoAprovado());
        var aprovado = pagamentoAprovado(orcamentoAprovado(), "123");

        assertThatThrownBy(() -> pendente.estornar(AGORA)).isInstanceOf(TransicaoInvalidaException.class);

        aprovado.estornar(AGORA);

        assertThat(aprovado.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(aprovado.getEstornadoEm()).isEqualTo(AGORA);
        assertThatThrownBy(() -> aprovado.estornar(AGORA)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void operacoesDePendenteRecusamOutrosStatus() {
        var aprovado = pagamentoAprovado(orcamentoAprovado(), "123");

        assertThatThrownBy(() -> aprovado.recusar("1", "x", "{}")).isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(() -> aprovado.registrarTentativaRecusada("x", "{}")).isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(() -> aprovado.expirar(AGORA.plus(Duration.ofDays(2)))).isInstanceOf(TransicaoInvalidaException.class);
    }
}
