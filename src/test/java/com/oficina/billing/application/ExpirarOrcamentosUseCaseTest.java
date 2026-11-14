package com.oficina.billing.application;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.OrcamentoExpirado;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.usecase.orcamento.ExpirarOrcamentosUseCase;
import com.oficina.billing.application.usecase.pagamento.ExpirarPagamentosUseCase;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.valueobject.StatusOrcamento;
import com.oficina.billing.support.UnidadeDeTrabalhoImediata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.oficina.billing.support.Fixtures.AGORA;
import static com.oficina.billing.support.Fixtures.VALIDADE;
import static com.oficina.billing.support.Fixtures.orcamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirarOrcamentosUseCaseTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock PublicadorMensagens publicador;
    @Mock MetricasBilling metricas;
    @Mock ExpirarPagamentosUseCase expirarPagamentos;

    @Test
    void expiraOsOrcamentosReivindicadosEDepoisOsPagamentosVencidos() {
        var vencimento = AGORA.plus(VALIDADE).plusSeconds(1);
        var useCase = new ExpirarOrcamentosUseCase(orcamentos, publicador, new UnidadeDeTrabalhoImediata(), metricas,
            () -> vencimento, expirarPagamentos, 50);
        var primeiro = orcamento();
        var segundo = orcamento();
        when(orcamentos.reivindicarVencidos(vencimento, 50)).thenReturn(List.of(primeiro, segundo));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        useCase.executar();

        assertThat(primeiro.getStatus()).isEqualTo(StatusOrcamento.EXPIRADO);
        assertThat(segundo.getExpiradoEm()).isEqualTo(vencimento);
        verify(publicador).publicar(Correlacao.de(primeiro), new OrcamentoExpirado(primeiro.getId(), vencimento));
        verify(publicador).publicar(Correlacao.de(segundo), new OrcamentoExpirado(segundo.getId(), vencimento));
        verify(metricas).registrarOrcamentoExpirado(primeiro.getId());
        verify(expirarPagamentos).executar();
    }
}
