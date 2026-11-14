package com.oficina.billing.domain.entity;

import com.oficina.billing.domain.exception.DomainException;
import com.oficina.billing.domain.exception.OrcamentoExpiradoException;
import com.oficina.billing.domain.exception.TokenDecisaoInvalidoException;
import com.oficina.billing.domain.exception.TransicaoInvalidaException;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.domain.valueobject.TokenDecisao;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class Orcamento {

    private final UUID id;
    private final UUID osId;
    private final UUID sagaId;
    private final UUID clienteId;
    private final String clienteNome;
    private final String clienteEmail;
    private final String veiculoPlaca;
    private final String veiculoModelo;
    private final List<ItemOrcamento> itens;
    private final BigDecimal total;
    private StatusOrcamento status;
    private final Instant criadoEm;
    private final Instant validoAte;
    private final String tokenDecisaoHash;
    private CanalDecisao canalDecisao;
    private Instant aprovadoEm;
    private Instant reprovadoEm;
    private Instant expiradoEm;
    private Instant canceladoEm;
    private String motivoCancelamento;
    private final Long versao;

    private Orcamento(DadosOrcamento dados) {
        this.id = dados.id();
        this.osId = dados.osId();
        this.sagaId = dados.sagaId();
        this.clienteId = dados.clienteId();
        this.clienteNome = dados.clienteNome();
        this.clienteEmail = dados.clienteEmail();
        this.veiculoPlaca = dados.veiculoPlaca();
        this.veiculoModelo = dados.veiculoModelo();
        this.itens = List.copyOf(dados.itens());
        this.total = dados.total();
        this.status = dados.status();
        this.criadoEm = dados.criadoEm();
        this.validoAte = dados.validoAte();
        this.tokenDecisaoHash = dados.tokenDecisaoHash();
        this.canalDecisao = dados.canalDecisao();
        this.aprovadoEm = dados.aprovadoEm();
        this.reprovadoEm = dados.reprovadoEm();
        this.expiradoEm = dados.expiradoEm();
        this.canceladoEm = dados.canceladoEm();
        this.motivoCancelamento = dados.motivoCancelamento();
        this.versao = dados.versao();
    }

    public static Orcamento criar(DadosNovoOrcamento dados, String tokenDecisaoHash, Instant agora, Duration validade) {
        exigir(dados.osId() != null, "O identificador da OS é obrigatório.");
        exigir(dados.sagaId() != null, "O identificador da saga é obrigatório.");
        exigir(dados.clienteId() != null, "O identificador do cliente é obrigatório.");
        exigirTexto(dados.clienteNome(), "O nome do cliente é obrigatório.");
        exigirTexto(dados.clienteEmail(), "O e-mail do cliente é obrigatório.");
        exigirTexto(dados.veiculoPlaca(), "A placa do veículo é obrigatória.");
        exigirTexto(dados.veiculoModelo(), "O modelo do veículo é obrigatório.");
        exigir(dados.itens() != null && !dados.itens().isEmpty(), "O orçamento deve ter ao menos um item.");
        exigir(tokenDecisaoHash != null && !tokenDecisaoHash.isBlank(), "O hash do token de decisão é obrigatório.");
        exigir(validade != null && !validade.isNegative() && !validade.isZero(), "A validade do orçamento deve ser positiva.");

        var total = dados.itens().stream()
            .map(ItemOrcamento::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_EVEN);

        return new Orcamento(new DadosOrcamento(
            UUID.randomUUID(), dados.osId(), dados.sagaId(), dados.clienteId(),
            dados.clienteNome().strip(), dados.clienteEmail().strip(),
            dados.veiculoPlaca().strip(), dados.veiculoModelo().strip(),
            dados.itens(), total, StatusOrcamento.AGUARDANDO_APROVACAO,
            agora, agora.plus(validade), tokenDecisaoHash,
            null, null, null, null, null, null, null));
    }

    public static Orcamento reconstituir(DadosOrcamento dados) {
        return new Orcamento(dados);
    }

    public boolean pertenceAo(UUID cliente) {
        return clienteId.equals(cliente);
    }

    public boolean vencidoEm(Instant instante) {
        return !instante.isBefore(validoAte);
    }

    public void validarToken(String tokenInformado) {
        if (!TokenDecisao.confere(tokenInformado, tokenDecisaoHash)) {
            throw new TokenDecisaoInvalidoException();
        }
    }

    public void exigirDecisaoPossivel(Instant agora) {
        if (status != StatusOrcamento.AGUARDANDO_APROVACAO) {
            throw new TransicaoInvalidaException(
                "O orçamento %s não aguarda decisão (status %s).".formatted(id, status));
        }
        if (vencidoEm(agora)) {
            throw new OrcamentoExpiradoException(id);
        }
    }

    public void aprovar(CanalDecisao canal, Instant agora) {
        exigirDecisaoPossivel(agora);
        this.status = StatusOrcamento.APROVADO;
        this.canalDecisao = canal;
        this.aprovadoEm = agora;
    }

    public void reprovar(CanalDecisao canal, Instant agora) {
        exigirDecisaoPossivel(agora);
        this.status = StatusOrcamento.REPROVADO;
        this.canalDecisao = canal;
        this.reprovadoEm = agora;
    }

    public void expirar(Instant agora) {
        if (status != StatusOrcamento.AGUARDANDO_APROVACAO || !vencidoEm(agora)) {
            throw new TransicaoInvalidaException(
                "O orçamento %s não pode expirar (status %s, válido até %s).".formatted(id, status, validoAte));
        }
        this.status = StatusOrcamento.EXPIRADO;
        this.expiradoEm = agora;
    }

    public boolean cancelar(String motivo, Instant agora) {
        if (status != StatusOrcamento.AGUARDANDO_APROVACAO && status != StatusOrcamento.APROVADO) {
            return false;
        }
        this.status = StatusOrcamento.CANCELADO;
        this.canceladoEm = agora;
        this.motivoCancelamento = motivo;
        return true;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new DomainException(mensagem);
        }
    }

    private static void exigirTexto(String valor, String mensagem) {
        exigir(valor != null && !valor.isBlank(), mensagem);
    }
}
