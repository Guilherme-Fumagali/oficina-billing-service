package com.oficina.billing.infrastructure.transaction;

import com.oficina.billing.application.port.UnidadeDeTrabalho;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class TransacaoSpring implements UnidadeDeTrabalho {

    private final TransactionTemplate template;

    public TransacaoSpring(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        return template.execute(status -> trabalho.get());
    }

    @Override
    public void executar(Runnable trabalho) {
        template.executeWithoutResult(status -> trabalho.run());
    }
}
