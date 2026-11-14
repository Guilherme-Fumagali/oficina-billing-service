package com.oficina.billing.application;

import com.oficina.billing.application.usecase.orcamento.AprovarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.DecidirPorTokenUseCase;
import com.oficina.billing.application.usecase.orcamento.Decisor;
import com.oficina.billing.application.usecase.orcamento.OrcamentoComPagamento;
import com.oficina.billing.application.usecase.orcamento.ReprovarOrcamentoUseCase;
import com.oficina.billing.domain.exception.TokenDecisaoInvalidoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.valueobject.DecisaoOrcamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.TOKEN;
import static com.oficina.billing.support.Fixtures.orcamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecidirPorTokenUseCaseTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock AprovarOrcamentoUseCase aprovar;
    @Mock ReprovarOrcamentoUseCase reprovar;

    DecidirPorTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DecidirPorTokenUseCase(orcamentos, aprovar, reprovar);
    }

    @Test
    void tokenValidoAprovaPeloCanalLink() {
        var orcamento = orcamento();
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        var esperado = new OrcamentoComPagamento(orcamento, null);
        when(aprovar.executar(orcamento.getId(), Decisor.link())).thenReturn(esperado);

        assertThat(useCase.executar(orcamento.getId(), TOKEN, DecisaoOrcamento.APROVAR)).isSameAs(esperado);
    }

    @Test
    void tokenValidoReprovaPeloCanalLink() {
        var orcamento = orcamento();
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(reprovar.executar(orcamento.getId(), Decisor.link())).thenReturn(orcamento);

        var resultado = useCase.executar(orcamento.getId(), TOKEN, DecisaoOrcamento.REPROVAR);

        assertThat(resultado.orcamento()).isSameAs(orcamento);
        assertThat(resultado.pagamento()).isNull();
        verify(reprovar).executar(orcamento.getId(), Decisor.link());
    }

    @Test
    void tokenInvalidoOuOrcamentoInexistenteTemAMesmaResposta() {
        var orcamento = orcamento();
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        var inexistente = UUID.randomUUID();
        when(orcamentos.buscarPorId(inexistente)).thenReturn(Optional.empty());
        var id = orcamento.getId();

        assertThatThrownBy(() -> useCase.executar(id, "token-errado", DecisaoOrcamento.APROVAR))
            .isInstanceOf(TokenDecisaoInvalidoException.class);
        assertThatThrownBy(() -> useCase.executar(inexistente, TOKEN, DecisaoOrcamento.APROVAR))
            .isInstanceOf(TokenDecisaoInvalidoException.class);
        verifyNoInteractions(aprovar, reprovar);
    }
}
