package com.oficina.billing.infrastructure.scheduling;

import com.oficina.billing.application.usecase.orcamento.ExpirarOrcamentosUseCase;
import com.oficina.billing.application.usecase.pagamento.ReconciliarPagamentosUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgendamentoPagamentos {

    private final ReconciliarPagamentosUseCase reconciliar;
    private final ExpirarOrcamentosUseCase expirar;

    public AgendamentoPagamentos(ReconciliarPagamentosUseCase reconciliar, ExpirarOrcamentosUseCase expirar) {
        this.reconciliar = reconciliar;
        this.expirar = expirar;
    }

    @Scheduled(fixedDelayString = "${app.pagamento.reconciliacao.intervalo:60000}",
        initialDelayString = "${app.pagamento.reconciliacao.atraso-inicial:60000}")
    public void reconciliarPagamentos() {
        reconciliar.executar();
    }

    @Scheduled(fixedDelayString = "${app.orcamento.expiracao.intervalo:300000}",
        initialDelayString = "${app.orcamento.expiracao.atraso-inicial:60000}")
    public void expirarPrazos() {
        expirar.executar();
    }
}
