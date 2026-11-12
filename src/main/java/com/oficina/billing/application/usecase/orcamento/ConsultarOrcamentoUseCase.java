package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;

import java.util.UUID;
import java.util.function.Supplier;

public class ConsultarOrcamentoUseCase {

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;

    public ConsultarOrcamentoUseCase(OrcamentoRepository orcamentos, PagamentoRepository pagamentos) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
    }

    public OrcamentoComPagamento buscar(UUID id) {
        return comPagamento(orcamentos.buscarPorId(id).orElseThrow(naoEncontrado(id)));
    }

    public OrcamentoComPagamento buscarDoCliente(UUID id, UUID clienteId) {
        return comPagamento(orcamentos.buscarPorId(id)
            .filter(o -> o.pertenceAo(clienteId))
            .orElseThrow(naoEncontrado(id)));
    }

    public OrcamentoComPagamento buscarPorOs(UUID osId) {
        return comPagamento(orcamentos.buscarPorOsId(osId).orElseThrow(naoEncontrado(osId)));
    }

    public OrcamentoComPagamento buscarPorOsDoCliente(UUID osId, UUID clienteId) {
        return comPagamento(orcamentos.buscarPorOsId(osId)
            .filter(o -> o.pertenceAo(clienteId))
            .orElseThrow(naoEncontrado(osId)));
    }

    private OrcamentoComPagamento comPagamento(Orcamento orcamento) {
        return new OrcamentoComPagamento(orcamento, pagamentos.buscarPorOrcamentoId(orcamento.getId()).orElse(null));
    }

    private static Supplier<RecursoNaoEncontradoException> naoEncontrado(UUID referencia) {
        return () -> new RecursoNaoEncontradoException("Orçamento", referencia);
    }
}
