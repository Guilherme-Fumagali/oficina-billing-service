package com.oficina.billing.domain;

import com.oficina.billing.domain.entity.DadosNovoOrcamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.exception.OrcamentoExpiradoException;
import com.oficina.billing.domain.exception.TokenDecisaoInvalidoException;
import com.oficina.billing.domain.exception.TransicaoInvalidaException;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.domain.valueobject.TokenDecisao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.TOKEN;
import static com.oficina.billing.support.Fixtures.VALIDADE;
import static com.oficina.billing.support.Fixtures.dadosNovoOrcamento;
import static com.oficina.billing.support.Fixtures.itens;
import static com.oficina.billing.support.Fixtures.orcamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcamentoTest {

    @Test
    void criarSomaOsSubtotaisEAguardaAprovacaoAteOFimDaValidade() {
        var clienteId = UUID.randomUUID();

        var orcamento = orcamento(clienteId);

        assertThat(orcamento.getTotal()).isEqualByComparingTo("210.00");
        assertThat(orcamento.getTotal().scale()).isEqualTo(2);
        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
        assertThat(orcamento.getValidoAte()).isEqualTo(AGORA.plus(VALIDADE));
        assertThat(orcamento.getCriadoEm()).isEqualTo(AGORA);
        assertThat(orcamento.getItens()).hasSize(2);
        assertThat(orcamento.pertenceAo(clienteId)).isTrue();
        assertThat(orcamento.pertenceAo(UUID.randomUUID())).isFalse();
        assertThat(orcamento.getVersao()).isNull();
    }

    @Test
    void criarExigeAoMenosUmItem() {
        var dados = dadosNovoOrcamento(UUID.randomUUID());
        var semItens = new DadosNovoOrcamento(dados.osId(), dados.sagaId(), dados.clienteId(), dados.clienteNome(),
            dados.clienteEmail(), dados.veiculoPlaca(), dados.veiculoModelo(), List.of());
        var hash = TokenDecisao.calcularHash(TOKEN);

        assertThatThrownBy(() -> Orcamento.criar(semItens, hash, AGORA, VALIDADE))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("ao menos um item");
    }

    static Stream<DadosNovoOrcamento> dadosIncompletos() {
        var base = dadosNovoOrcamento(UUID.randomUUID());
        return Stream.of(
            new DadosNovoOrcamento(null, base.sagaId(), base.clienteId(), "Nome", "e@x.com", "ABC1234", "Gol", base.itens()),
            new DadosNovoOrcamento(base.osId(), null, base.clienteId(), "Nome", "e@x.com", "ABC1234", "Gol", base.itens()),
            new DadosNovoOrcamento(base.osId(), base.sagaId(), null, "Nome", "e@x.com", "ABC1234", "Gol", base.itens()),
            new DadosNovoOrcamento(base.osId(), base.sagaId(), base.clienteId(), " ", "e@x.com", "ABC1234", "Gol", base.itens()),
            new DadosNovoOrcamento(base.osId(), base.sagaId(), base.clienteId(), "Nome", null, "ABC1234", "Gol", base.itens()),
            new DadosNovoOrcamento(base.osId(), base.sagaId(), base.clienteId(), "Nome", "e@x.com", "", "Gol", base.itens()),
            new DadosNovoOrcamento(base.osId(), base.sagaId(), base.clienteId(), "Nome", "e@x.com", "ABC1234", null, base.itens()),
            new DadosNovoOrcamento(base.osId(), base.sagaId(), base.clienteId(), "Nome", "e@x.com", "ABC1234", "Gol", null));
    }

    @ParameterizedTest
    @MethodSource("dadosIncompletos")
    void criarRecusaDadosObrigatoriosAusentes(DadosNovoOrcamento dados) {
        var hash = TokenDecisao.calcularHash(TOKEN);

        assertThatThrownBy(() -> Orcamento.criar(dados, hash, AGORA, VALIDADE))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void criarExigeHashDoTokenEValidadePositiva() {
        var dados = dadosNovoOrcamento(UUID.randomUUID());
        var hash = TokenDecisao.calcularHash(TOKEN);

        assertThatThrownBy(() -> Orcamento.criar(dados, null, AGORA, VALIDADE)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Orcamento.criar(dados, hash, AGORA, Duration.ZERO)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Orcamento.criar(dados, hash, AGORA, null)).isInstanceOf(DomainException.class);
    }

    @Test
    void aprovarRegistraCanalEData() {
        var orcamento = orcamento();
        var instante = AGORA.plusSeconds(30);

        orcamento.aprovar(CanalDecisao.LINK, instante);

        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(orcamento.getCanalDecisao()).isEqualTo(CanalDecisao.LINK);
        assertThat(orcamento.getAprovadoEm()).isEqualTo(instante);
    }

    @Test
    void reprovarRegistraCanalEData() {
        var orcamento = orcamento();

        orcamento.reprovar(CanalDecisao.BALCAO, AGORA);

        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.REPROVADO);
        assertThat(orcamento.getCanalDecisao()).isEqualTo(CanalDecisao.BALCAO);
        assertThat(orcamento.getReprovadoEm()).isEqualTo(AGORA);
    }

    @Test
    void decisaoSoAconteceUmaVez() {
        var orcamento = orcamento();
        orcamento.aprovar(CanalDecisao.CLIENTE, AGORA);

        assertThatThrownBy(() -> orcamento.reprovar(CanalDecisao.CLIENTE, AGORA))
            .isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(() -> orcamento.aprovar(CanalDecisao.CLIENTE, AGORA))
            .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void decisaoAposAValidadeERecusadaComoExpirada() {
        var orcamento = orcamento();
        var depoisDaValidade = AGORA.plus(VALIDADE);

        assertThatThrownBy(() -> orcamento.aprovar(CanalDecisao.CLIENTE, depoisDaValidade))
            .isInstanceOf(OrcamentoExpiradoException.class);
        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
    }

    @Test
    void expirarSoQuandoVencidoEAguardandoDecisao() {
        var orcamento = orcamento();

        assertThatThrownBy(() -> orcamento.expirar(AGORA)).isInstanceOf(TransicaoInvalidaException.class);

        var vencimento = AGORA.plus(VALIDADE).plusSeconds(1);
        orcamento.expirar(vencimento);

        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.EXPIRADO);
        assertThat(orcamento.getExpiradoEm()).isEqualTo(vencimento);
        assertThatThrownBy(() -> orcamento.expirar(vencimento)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void cancelarAlteraOrcamentoAguardandoOuAprovado() {
        var aguardando = orcamento();
        var aprovado = orcamento();
        aprovado.aprovar(CanalDecisao.CLIENTE, AGORA);

        assertThat(aguardando.cancelar("pagamento recusado", AGORA)).isTrue();
        assertThat(aprovado.cancelar("falha na execução", AGORA)).isTrue();

        assertThat(aguardando.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(aprovado.getCanceladoEm()).isEqualTo(AGORA);
        assertThat(aprovado.getMotivoCancelamento()).isEqualTo("falha na execução");
    }

    @Test
    void cancelarEIdempotenteENaoReabreOrcamentoEncerrado() {
        var cancelado = orcamento();
        cancelado.cancelar("primeiro", AGORA);
        var reprovado = orcamento();
        reprovado.reprovar(CanalDecisao.CLIENTE, AGORA);

        assertThat(cancelado.cancelar("segundo", AGORA.plusSeconds(5))).isFalse();
        assertThat(cancelado.getMotivoCancelamento()).isEqualTo("primeiro");
        assertThat(reprovado.cancelar("qualquer", AGORA)).isFalse();
        assertThat(reprovado.getStatus()).isEqualTo(StatusOrcamento.REPROVADO);
    }

    @Test
    void validarTokenAceitaSomenteOTokenDoEmail() {
        var orcamento = orcamento();

        orcamento.validarToken(TOKEN);
        assertThatThrownBy(() -> orcamento.validarToken("outro-token")).isInstanceOf(TokenDecisaoInvalidoException.class);
        assertThatThrownBy(() -> orcamento.validarToken(null)).isInstanceOf(TokenDecisaoInvalidoException.class);
    }

    @Test
    void totalArredondaParaDuasCasas() {
        var dados = dadosNovoOrcamento(UUID.randomUUID());
        var orcamento = Orcamento.criar(dados, TokenDecisao.calcularHash(TOKEN), AGORA, VALIDADE);

        assertThat(orcamento.getTotal()).isEqualTo(new BigDecimal("210.00"));
        assertThat(itens()).allSatisfy(item -> assertThat(item.getSubtotal().scale()).isEqualTo(2));
    }
}
