package com.oficina.billing.application.usecase.pagamento;

import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;

import java.util.UUID;

public class ConsultarPagamentoUseCase {

    private final PagamentoRepository pagamentos;
    private final OrcamentoRepository orcamentos;

    public ConsultarPagamentoUseCase(PagamentoRepository pagamentos, OrcamentoRepository orcamentos) {
        this.pagamentos = pagamentos;
        this.orcamentos = orcamentos;
    }

    public Pagamento buscar(UUID id) {
        return pagamentos.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", id));
    }

    public Pagamento buscarDoCliente(UUID id, UUID clienteId) {
        return pagamentos.buscarPorId(id)
            .filter(p -> orcamentos.buscarPorId(p.getOrcamentoId()).map(o -> o.pertenceAo(clienteId)).orElse(false))
            .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", id));
    }
}
