package com.oficina.billing.domain;

import com.oficina.billing.domain.valueobject.TokenDecisao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDecisaoTest {

    @Test
    void geraTokenDe256BitsEmBase64UrlEGuardaApenasOHash() {
        var token = TokenDecisao.gerar();

        assertThat(token.valor()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(token.hash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(token.hash()).isEqualTo(TokenDecisao.calcularHash(token.valor()));
        assertThat(token.hash()).isNotEqualTo(token.valor());
    }

    @Test
    void tokensGeradosSaoDistintos() {
        assertThat(TokenDecisao.gerar().valor()).isNotEqualTo(TokenDecisao.gerar().valor());
    }

    @Test
    void hashSegueOVetorConhecidoDoSha256() {
        assertThat(TokenDecisao.calcularHash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void confereSomenteOTokenQueGerouOHash() {
        var token = TokenDecisao.gerar();

        assertThat(TokenDecisao.confere(token.valor(), token.hash())).isTrue();
        assertThat(TokenDecisao.confere(TokenDecisao.gerar().valor(), token.hash())).isFalse();
        assertThat(TokenDecisao.confere(null, token.hash())).isFalse();
        assertThat(TokenDecisao.confere(" ", token.hash())).isFalse();
        assertThat(TokenDecisao.confere(token.valor(), null)).isFalse();
    }

    @Test
    void toStringNaoExpoeOValor() {
        var token = TokenDecisao.gerar();

        assertThat(token.toString()).doesNotContain(token.valor()).contains("***");
    }
}
