package com.oficina.billing.application.usecase.orcamento;

import com.oficina.billing.application.comando.ComandoCancelarOrcamento;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.comando.TiposComando;
import com.oficina.billing.application.mensagem.ComandoRejeitado;
import com.oficina.billing.application.mensagem.OrcamentoCancelado;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.repository.OrcamentoRepository;

public class CancelarOrcamentoUseCase {

    static final String CODIGO_ORCAMENTO_NAO_ENCONTRADO = "ORCAMENTO_NAO_ENCONTRADO";

    private final OrcamentoRepository orcamentos;
    private final MensagensProcessadas mensagensProcessadas;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Relogio relogio;

    public CancelarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                    MensagensProcessadas mensagensProcessadas,
                                    PublicadorMensagens publicador,
                                    UnidadeDeTrabalho unidadeDeTrabalho,
                                    Relogio relogio) {
        this.orcamentos = orcamentos;
        this.mensagensProcessadas = mensagensProcessadas;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public ResultadoComando executar(ComandoCancelarOrcamento comando) {
        return unidadeDeTrabalho.executar(() -> {
            if (!mensagensProcessadas.registrar(comando.mensagemId(), TiposComando.CANCELAR_ORCAMENTO)) {
                return ResultadoComando.DUPLICADA;
            }
            var encontrado = orcamentos.buscarPorOsId(comando.osId());
            if (encontrado.isEmpty()) {
                publicador.publicar(comando.correlacao(), new ComandoRejeitado(TiposComando.CANCELAR_ORCAMENTO,
                    "Não há orçamento para a OS %s.".formatted(comando.osId()), CODIGO_ORCAMENTO_NAO_ENCONTRADO));
                return ResultadoComando.REJEITADA;
            }
            var orcamento = encontrado.get();
            if (orcamento.cancelar(comando.motivo(), relogio.agora())) {
                orcamentos.salvar(orcamento);
            }
            publicador.publicar(comando.correlacao(), new OrcamentoCancelado(orcamento.getId(), comando.motivo()));
            return ResultadoComando.APLICADA;
        });
    }
}
