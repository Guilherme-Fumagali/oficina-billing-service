package com.oficina.billing.application;

import com.oficina.billing.application.comando.ComandoCancelarOrcamento;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.mensagem.OrcamentoCancelado;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.usecase.orcamento.CancelarOrcamentoUseCase;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.orcamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarOrcamentoUseCaseTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock MensagensProcessadas mensagensProcessadas;
    @Mock PublicadorMensagens publicador;

    CancelarOrcamentoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelarOrcamentoUseCase(orcamentos, mensagensProcessadas, publicador,
            new UnidadeDeTrabalhoImediata(), () -> AGORA);
    }

    @Test
    void cancelaOrcamentoAprovadoEResponde() {
        var orcamento = orcamento();
        orcamento.aprovar(CanalDecisao.CLIENTE, AGORA);
        var comando = comando(orcamento.getOsId());
        when(mensagensProcessadas.registrar(comando.mensagemId(), "CancelarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(orcamento));

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.APLICADA);

        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        verify(orcamentos).salvar(orcamento);
        verify(publicador).publicar(comando.correlacao(), new OrcamentoCancelado(orcamento.getId(), "pagamento recusado"));
    }

    @Test
    void cancelamentoRepetidoRespondeSemAlterarOOrcamento() {
        var orcamento = orcamento();
        orcamento.cancelar("anterior", AGORA);
        var comando = comando(orcamento.getOsId());
        when(mensagensProcessadas.registrar(comando.mensagemId(), "CancelarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(orcamento));

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.APLICADA);

        verify(orcamentos, never()).salvar(any());
        verify(publicador).publicar(comando.correlacao(), new OrcamentoCancelado(orcamento.getId(), "pagamento recusado"));
    }

    @Test
    void semOrcamentoParaAOsRespondeOrcamentoCanceladoSemIdentificador() {
        var comando = comando(UUID.randomUUID());
        when(mensagensProcessadas.registrar(comando.mensagemId(), "CancelarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.APLICADA);

        verify(publicador).publicar(comando.correlacao(), new OrcamentoCancelado(null, "pagamento recusado"));
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void duplicataNaoProduzEfeito() {
        var comando = comando(UUID.randomUUID());
        when(mensagensProcessadas.registrar(comando.mensagemId(), "CancelarOrcamento")).thenReturn(false);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.DUPLICADA);

        verifyNoInteractions(orcamentos, publicador);
    }

    private static ComandoCancelarOrcamento comando(UUID osId) {
        return new ComandoCancelarOrcamento(UUID.randomUUID(), UUID.randomUUID(), osId, "pagamento recusado");
    }
}
