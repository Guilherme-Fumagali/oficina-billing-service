package com.oficina.billing.application.usecase.comando;

import com.oficina.billing.application.comando.RejeicaoComando;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.mensagem.ComandoRejeitado;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.UnidadeDeTrabalho;

public class RejeitarComandoUseCase {

    private final MensagensProcessadas mensagensProcessadas;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public RejeitarComandoUseCase(MensagensProcessadas mensagensProcessadas,
                                  PublicadorMensagens publicador,
                                  UnidadeDeTrabalho unidadeDeTrabalho) {
        this.mensagensProcessadas = mensagensProcessadas;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
    }

    public ResultadoComando executar(RejeicaoComando rejeicao) {
        return unidadeDeTrabalho.executar(() -> {
            if (!mensagensProcessadas.registrar(rejeicao.mensagemId(), rejeicao.comando())) {
                return ResultadoComando.DUPLICADA;
            }
            publicador.publicar(rejeicao.correlacao(),
                new ComandoRejeitado(rejeicao.comando(), rejeicao.motivo(), rejeicao.codigo()));
            return ResultadoComando.REJEITADA;
        });
    }
}
