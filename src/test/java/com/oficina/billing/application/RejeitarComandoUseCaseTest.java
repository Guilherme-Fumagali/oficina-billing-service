package com.oficina.billing.application;

import com.oficina.billing.application.comando.RejeicaoComando;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.mensagem.ComandoRejeitado;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.usecase.comando.RejeitarComandoUseCase;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejeitarComandoUseCaseTest {

    @Mock MensagensProcessadas mensagensProcessadas;
    @Mock PublicadorMensagens publicador;

    @Test
    void registraAMensagemERespondeComandoRejeitado() {
        var useCase = new RejeitarComandoUseCase(mensagensProcessadas, publicador, new UnidadeDeTrabalhoImediata());
        var rejeicao = new RejeicaoComando(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "GerarOrcamento", "Dados inválidos", "DADOS_INVALIDOS");
        when(mensagensProcessadas.registrar(rejeicao.mensagemId(), "GerarOrcamento")).thenReturn(true);

        assertThat(useCase.executar(rejeicao)).isEqualTo(ResultadoComando.REJEITADA);

        verify(publicador).publicar(rejeicao.correlacao(),
            new ComandoRejeitado("GerarOrcamento", "Dados inválidos", "DADOS_INVALIDOS"));
    }

    @Test
    void duplicataNaoRespondeDeNovo() {
        var useCase = new RejeitarComandoUseCase(mensagensProcessadas, publicador, new UnidadeDeTrabalhoImediata());
        var rejeicao = new RejeicaoComando(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "EstornarPagamento", "x", "DADOS_INVALIDOS");
        when(mensagensProcessadas.registrar(rejeicao.mensagemId(), "EstornarPagamento")).thenReturn(false);

        assertThat(useCase.executar(rejeicao)).isEqualTo(ResultadoComando.DUPLICADA);

        verifyNoInteractions(publicador);
    }
}
