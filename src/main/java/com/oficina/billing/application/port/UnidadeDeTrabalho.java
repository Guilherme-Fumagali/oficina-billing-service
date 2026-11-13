package com.oficina.billing.application.port;

import java.util.function.Supplier;

public interface UnidadeDeTrabalho {

    <T> T executar(Supplier<T> trabalho);

    void executar(Runnable trabalho);
}
