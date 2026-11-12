package com.oficina.billing.application;

import com.oficina.billing.application.usecase.orcamento.ConsultarOrcamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.ConsultarPagamentoUseCase;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.oficina.billing.support.Fixtures.orcamento;
import static com.oficina.billing.support.Fixtures.pagamentoPendente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultasUseCaseTest {

    @Mock OrcamentoRepository orcamentos;
    @Mock PagamentoRepository pagamentos;

    ConsultarOrcamentoUseCase consultarOrcamento;
    ConsultarPagamentoUseCase consultarPagamento;

    @BeforeEach
    void setUp() {
        consultarOrcamento = new ConsultarOrcamentoUseCase(orcamentos, pagamentos);
        consultarPagamento = new ConsultarPagamentoUseCase(pagamentos, orcamentos);
    }

    @Test
    void funcionarioConsultaQualquerOrcamentoComOPagamento() {
        var orcamento = orcamento();
        var pagamento = pagamentoPendente(orcamento);
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.of(pagamento));

        var consulta = consultarOrcamento.buscar(orcamento.getId());

        assertThat(consulta.orcamento()).isSameAs(orcamento);
        assertThat(consulta.pagamento()).isSameAs(pagamento);
    }

    @Test
    void clienteConsultaApenasOsProprios() {
        var clienteId = UUID.randomUUID();
        var orcamento = orcamento(clienteId);
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(orcamentos.buscarPorOsId(orcamento.getOsId())).thenReturn(Optional.of(orcamento));
        when(pagamentos.buscarPorOrcamentoId(orcamento.getId())).thenReturn(Optional.empty());
        var id = orcamento.getId();
        var osId = orcamento.getOsId();
        var outro = UUID.randomUUID();

        assertThat(consultarOrcamento.buscarDoCliente(id, clienteId).pagamento()).isNull();
        assertThat(consultarOrcamento.buscarPorOsDoCliente(osId, clienteId).orcamento()).isSameAs(orcamento);
        assertThatThrownBy(() -> consultarOrcamento.buscarDoCliente(id, outro)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> consultarOrcamento.buscarPorOsDoCliente(osId, outro)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void consultaPorOsInexistenteRetornaNaoEncontrado() {
        var osId = UUID.randomUUID();
        when(orcamentos.buscarPorOsId(osId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultarOrcamento.buscarPorOs(osId)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void pagamentoVisivelAoFuncionarioEAoDonoDoOrcamento() {
        var clienteId = UUID.randomUUID();
        var orcamento = orcamento(clienteId);
        var pagamento = pagamentoPendente(orcamento);
        when(pagamentos.buscarPorId(pagamento.getId())).thenReturn(Optional.of(pagamento));
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        var id = pagamento.getId();
        var outro = UUID.randomUUID();
        var inexistente = UUID.randomUUID();
        when(pagamentos.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThat(consultarPagamento.buscar(id)).isSameAs(pagamento);
        assertThat(consultarPagamento.buscarDoCliente(id, clienteId)).isSameAs(pagamento);
        assertThatThrownBy(() -> consultarPagamento.buscarDoCliente(id, outro)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> consultarPagamento.buscar(inexistente)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
