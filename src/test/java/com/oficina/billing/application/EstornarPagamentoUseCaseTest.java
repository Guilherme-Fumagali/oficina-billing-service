package com.oficina.billing.application;

import com.oficina.billing.application.comando.ComandoEstornarPagamento;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.exception.GatewayPagamentoIndisponivelException;
import com.oficina.billing.application.mensagem.PagamentoEstornado;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.usecase.pagamento.EstornarPagamentoUseCase;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.orcamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoComStatus;
import static com.oficina.billing.support.Fixtures.pagamentoPendente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstornarPagamentoUseCaseTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock PagamentoRepository pagamentos;
    @Mock GatewayPagamento gateway;
    @Mock MensagensProcessadas mensagensProcessadas;
    @Mock PublicadorMensagens publicador;
    @Mock MetricasBilling metricas;

    EstornarPagamentoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EstornarPagamentoUseCase(orcamentos, pagamentos, gateway, mensagensProcessadas, publicador,
            new UnidadeDeTrabalhoImediata(), metricas, () -> AGORA);
    }

    @Test
    void estornaPagamentoAprovadoNoGatewayComChaveDeIdempotenciaDoPagamento() {
        var orcamento = orcamentoAprovado();
        var pagamento = pagamentoAprovado(orcamento, "300");
        var comando = comando(orcamento.getOsId());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(false);
        when(mensagensProcessadas.registrar(comando.mensagemId(), "EstornarPagamento")).thenReturn(true);
        when(pagamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(pagamento));
        when(orcamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(orcamento));
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.APLICADA);

        verify(gateway).estornar("300", pagamento.getId().toString());
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        verify(publicador).publicar(comando.correlacao(),
            new PagamentoEstornado(orcamento.getId(), pagamento.getId(), pagamento.getValor(), AGORA));
        verify(metricas).registrarEstorno("sucesso", orcamento.getId(), "300");
    }

    @Test
    void semPagamentoAprovadoRespondeValorZero() {
        var orcamento = orcamentoAprovado();
        var pendente = pagamentoPendente(orcamento);
        var comando = comando(orcamento.getOsId());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(false);
        when(mensagensProcessadas.registrar(comando.mensagemId(), "EstornarPagamento")).thenReturn(true);
        when(pagamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(pendente));
        when(orcamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(orcamento));

        useCase.executar(comando);

        verifyNoInteractions(gateway);
        verify(publicador).publicar(comando.correlacao(),
            new PagamentoEstornado(orcamento.getId(), pendente.getId(), new BigDecimal("0.00"), AGORA));
    }

    @Test
    void semOrcamentoNemPagamentoRespondeValorZero() {
        var comando = comando(UUID.randomUUID());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(false);
        when(mensagensProcessadas.registrar(comando.mensagemId(), "EstornarPagamento")).thenReturn(true);
        when(pagamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());
        when(orcamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());

        useCase.executar(comando);

        verify(publicador).publicar(comando.correlacao(), new PagamentoEstornado(null, null, new BigDecimal("0.00"), AGORA));
    }

    @Test
    void comandoRepetidoAposOEstornoRepeteARespostaSemChamarOGateway() {
        var orcamento = orcamentoAprovado();
        var estornado = pagamentoComStatus(orcamento, StatusPagamento.ESTORNADO);
        var comando = comando(orcamento.getOsId());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(false);
        when(mensagensProcessadas.registrar(comando.mensagemId(), "EstornarPagamento")).thenReturn(true);
        when(pagamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(estornado));
        when(orcamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(orcamento));

        useCase.executar(comando);

        verifyNoInteractions(gateway);
        verify(publicador).publicar(comando.correlacao(),
            new PagamentoEstornado(orcamento.getId(), estornado.getId(), estornado.getValor(), estornado.getEstornadoEm()));
    }

    @Test
    void duplicataExataNaoChamaOGateway() {
        var comando = comando(UUID.randomUUID());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(true);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.DUPLICADA);

        verifyNoInteractions(gateway, publicador, pagamentos);
    }

    @Test
    void duplicataConcorrenteDetectadaNaTransacao() {
        var comando = comando(UUID.randomUUID());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(false);
        when(pagamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());
        when(mensagensProcessadas.registrar(comando.mensagemId(), "EstornarPagamento")).thenReturn(false);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.DUPLICADA);

        verifyNoInteractions(publicador);
    }

    @Test
    void falhaDoGatewayPropagaParaNovaTentativaPelaFila() {
        var orcamento = orcamentoAprovado();
        var pagamento = pagamentoAprovado(orcamento, "301");
        var comando = comando(orcamento.getOsId());
        when(mensagensProcessadas.jaProcessada(comando.mensagemId())).thenReturn(false);
        when(pagamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(pagamento));
        doThrow(new GatewayPagamentoIndisponivelException("fora", null)).when(gateway).estornar(anyString(), anyString());

        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(GatewayPagamentoIndisponivelException.class);

        verify(metricas).registrarEstorno("falha", orcamento.getId(), "301");
        verify(mensagensProcessadas, never()).registrar(any(), anyString());
        verifyNoInteractions(publicador);
    }

    private static ComandoEstornarPagamento comando(UUID osId) {
        return new ComandoEstornarPagamento(UUID.randomUUID(), UUID.randomUUID(), osId, "falha na execução");
    }
}
