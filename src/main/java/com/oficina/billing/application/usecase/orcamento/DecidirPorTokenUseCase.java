package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.domain.exception.TokenDecisaoInvalidoException;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.valueobject.DecisaoOrcamento;

import java.util.UUID;

public class DecidirPorTokenUseCase {

    private final OrcamentoRepository orcamentos;
    private final AprovarOrcamentoUseCase aprovar;
    private final ReprovarOrcamentoUseCase reprovar;

    public DecidirPorTokenUseCase(OrcamentoRepository orcamentos,
                                  AprovarOrcamentoUseCase aprovar,
                                  ReprovarOrcamentoUseCase reprovar) {
        this.orcamentos = orcamentos;
        this.aprovar = aprovar;
        this.reprovar = reprovar;
    }

    public OrcamentoComPagamento executar(UUID orcamentoId, String token, DecisaoOrcamento decisao) {
        var orcamento = orcamentos.buscarPorId(orcamentoId).orElseThrow(TokenDecisaoInvalidoException::new);
        orcamento.validarToken(token);
        return switch (decisao) {
            case APROVAR -> aprovar.executar(orcamentoId, Decisor.link());
            case REPROVAR -> new OrcamentoComPagamento(reprovar.executar(orcamentoId, Decisor.link()), null);
        };
    }
}
