package com.oficina.billing.application;

import com.oficina.billing.application.comando.ComandoGerarOrcamento;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.mensagem.ComandoRejeitado;
import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.EventoBilling;
import com.oficina.billing.application.mensagem.OrcamentoGerado;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.usecase.orcamento.GerarOrcamentoUseCase;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.valueobject.TipoItem;
import com.oficina.billing.domain.valueobject.TokenDecisao;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.VALIDADE;
import static com.oficina.billing.support.Fixtures.orcamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarOrcamentoUseCaseTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock MensagensProcessadas mensagensProcessadas;
    @Mock PublicadorMensagens publicador;
    @Mock NotificacaoCliente notificacao;
    @Mock MetricasBilling metricas;

    GerarOrcamentoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GerarOrcamentoUseCase(orcamentos, mensagensProcessadas, publicador,
            new UnidadeDeTrabalhoImediata(), notificacao, metricas, () -> AGORA, VALIDADE);
    }

    @Test
    void criaOrcamentoPublicaOrcamentoGeradoEEnviaOTokenPorEmail() {
        var comando = comando(List.of(item(1, "120.00"), item(2, "45.00")));
        when(mensagensProcessadas.registrar(comando.mensagemId(), "GerarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        var resultado = useCase.executar(comando);

        assertThat(resultado).isEqualTo(ResultadoComando.APLICADA);
        var salvo = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).salvar(salvo.capture());
        assertThat(salvo.getValue().getTotal()).isEqualByComparingTo("210.00");
        assertThat(salvo.getValue().getValidoAte()).isEqualTo(AGORA.plus(VALIDADE));

        var correlacao = ArgumentCaptor.forClass(Correlacao.class);
        var evento = ArgumentCaptor.forClass(EventoBilling.class);
        verify(publicador).publicar(correlacao.capture(), evento.capture());
        assertThat(correlacao.getValue()).isEqualTo(new Correlacao(comando.sagaId(), comando.osId(), comando.mensagemId()));
        assertThat(evento.getValue()).isInstanceOfSatisfying(OrcamentoGerado.class, gerado -> {
            assertThat(gerado.orcamentoId()).isEqualTo(salvo.getValue().getId());
            assertThat(gerado.total()).isEqualByComparingTo("210.00");
        });

        var token = ArgumentCaptor.forClass(String.class);
        verify(notificacao).enviarOrcamento(eq(salvo.getValue()), token.capture());
        assertThat(TokenDecisao.calcularHash(token.getValue())).isEqualTo(salvo.getValue().getTokenDecisaoHash());
        verify(metricas).registrarOrcamentoGerado(salvo.getValue().getId());
    }

    @Test
    void mensagemDuplicadaNaoProduzEfeito() {
        var comando = comando(List.of(item(1, "10.00")));
        when(mensagensProcessadas.registrar(comando.mensagemId(), "GerarOrcamento")).thenReturn(false);

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.DUPLICADA);

        verifyNoInteractions(orcamentos, publicador, notificacao);
    }

    @Test
    void comandoRepetidoParaAMesmaOsRepublicaOOrcamentoExistente() {
        var comando = comando(List.of(item(1, "10.00")));
        var existente = orcamento();
        when(mensagensProcessadas.registrar(comando.mensagemId(), "GerarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.of(existente));

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.APLICADA);

        verify(publicador).publicar(any(), eq(new OrcamentoGerado(existente.getId(), existente.getTotal(), existente.getValidoAte())));
        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(notificacao);
    }

    @Test
    void dadosInvalidosRespondemComandoRejeitado() {
        var comando = comando(List.of(item(0, "10.00")));
        when(mensagensProcessadas.registrar(comando.mensagemId(), "GerarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.REJEITADA);

        var evento = ArgumentCaptor.forClass(EventoBilling.class);
        verify(publicador).publicar(any(), evento.capture());
        assertThat(evento.getValue()).isInstanceOfSatisfying(ComandoRejeitado.class, rejeitado -> {
            assertThat(rejeitado.comando()).isEqualTo("GerarOrcamento");
            assertThat(rejeitado.codigo()).isEqualTo("DADOS_INVALIDOS");
            assertThat(rejeitado.motivo()).contains("quantidade");
        });
        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(notificacao);
    }

    @Test
    void itemAusenteOuListaNulaTambemSaoRejeitados() {
        var itensComNulo = new ArrayList<ComandoGerarOrcamento.Item>();
        itensComNulo.add(null);
        var comNulo = comando(itensComNulo);
        var semLista = comando(null);
        when(mensagensProcessadas.registrar(any(), anyString())).thenReturn(true);
        when(orcamentos.buscarPorOsId(any())).thenReturn(Optional.empty());

        assertThat(useCase.executar(comNulo)).isEqualTo(ResultadoComando.REJEITADA);
        assertThat(useCase.executar(semLista)).isEqualTo(ResultadoComando.REJEITADA);
    }

    @Test
    void falhaNoEnvioDoEmailNaoDesfazOOrcamento() {
        var comando = comando(List.of(item(1, "99.90")));
        when(mensagensProcessadas.registrar(comando.mensagemId(), "GerarOrcamento")).thenReturn(true);
        when(orcamentos.buscarPorOsId(comando.osId())).thenReturn(Optional.empty());
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        var falha = new IllegalStateException("SMTP fora");
        doThrow(falha).when(notificacao).enviarOrcamento(any(), anyString());

        assertThat(useCase.executar(comando)).isEqualTo(ResultadoComando.APLICADA);

        verify(metricas).registrarFalha(eq("notificacao_orcamento"), anyString(), eq(falha));
    }

    private static ComandoGerarOrcamento comando(List<ComandoGerarOrcamento.Item> itens) {
        return new ComandoGerarOrcamento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Maria Souza", "maria@example.com", "ABC1D23", "Corolla", itens);
    }

    private static ComandoGerarOrcamento.Item item(int quantidade, String preco) {
        return new ComandoGerarOrcamento.Item(TipoItem.SERVICO, UUID.randomUUID(), "Serviço", quantidade, new BigDecimal(preco));
    }
}
