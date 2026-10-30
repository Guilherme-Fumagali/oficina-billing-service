package com.oficina.billing.support;

import com.oficina.billing.application.port.UnidadeDeTrabalho;

import java.util.function.Supplier;

public class UnidadeDeTrabalhoImediata implements UnidadeDeTrabalho {

    private int execucoes;

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        execucoes++;
        return trabalho.get();
    }

    @Override
    public void executar(Runnable trabalho) {
        execucoes++;
        trabalho.run();
    }

    public int execucoes() {
        return execucoes;
    }
}
