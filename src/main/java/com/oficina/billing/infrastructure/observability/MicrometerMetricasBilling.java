package com.oficina.billing.infrastructure.observability;

import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MicrometerMetricasBilling implements MetricasBilling {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricasBilling.class);

    static final String ORCAMENTOS_GERADOS = "oficina.orcamento.gerados";
    static final String ORCAMENTO_DECISOES = "oficina.orcamento.decisoes";
    static final String ORCAMENTO_VALOR_APROVADO = "oficina.orcamento.valor_aprovado";
    static final String PAGAMENTO_RESULTADOS = "oficina.pagamento.resultados";
    static final String PAGAMENTO_WEBHOOKS = "oficina.pagamento.webhooks";
    static final String PAGAMENTO_RECONCILIACOES = "oficina.pagamento.reconciliacoes";
    static final String PAGAMENTO_ESTORNOS = "oficina.pagamento.estornos";
    static final String INTEGRACAO_MERCADOPAGO = "oficina.integracao.mercadopago";
    static final String MENSAGERIA_PUBLICADAS = "oficina.mensageria.publicadas";
    static final String MENSAGERIA_CONSUMIDAS = "oficina.mensageria.consumidas";

    private static final String RESULTADO = "resultado";
    private static final String ORCAMENTO_ID = "orcamento.id";

    private final MeterRegistry registry;
    private final DistributionSummary valorAprovado;

    public MicrometerMetricasBilling(MeterRegistry registry) {
        this.registry = registry;
        this.valorAprovado = DistributionSummary.builder(ORCAMENTO_VALOR_APROVADO)
            .baseUnit("BRL")
            .register(registry);
    }

    @Override
    public void registrarOrcamentoGerado(UUID orcamentoId) {
        registry.counter(ORCAMENTOS_GERADOS).increment();
        log.atInfo().addKeyValue(ORCAMENTO_ID, orcamentoId).log("Orçamento {} gerado e enviado para aprovação", orcamentoId);
    }

    @Override
    public void registrarAprovacao(UUID orcamentoId, CanalDecisao canal, BigDecimal valor) {
        registry.counter(ORCAMENTO_DECISOES, RESULTADO, "aprovado", "canal", canal.name()).increment();
        valorAprovado.record(valor.doubleValue());
        log.atInfo().addKeyValue(ORCAMENTO_ID, orcamentoId).log("Orçamento {} aprovado pelo canal {}", orcamentoId, canal);
    }

    @Override
    public void registrarReprovacao(UUID orcamentoId, CanalDecisao canal) {
        registry.counter(ORCAMENTO_DECISOES, RESULTADO, "reprovado", "canal", canal.name()).increment();
        log.atInfo().addKeyValue(ORCAMENTO_ID, orcamentoId).log("Orçamento {} reprovado pelo canal {}", orcamentoId, canal);
    }

    @Override
    public void registrarOrcamentoExpirado(UUID orcamentoId) {
        registry.counter(ORCAMENTO_DECISOES, RESULTADO, "expirado", "canal", "SISTEMA").increment();
        log.atInfo().addKeyValue(ORCAMENTO_ID, orcamentoId).log("Orçamento {} expirado sem decisão", orcamentoId);
    }

    @Override
    public void registrarResultadoPagamento(UUID pagamentoId, StatusPagamento status) {
        registry.counter(PAGAMENTO_RESULTADOS, "status", status.name()).increment();
        log.atInfo().addKeyValue("pagamento.id", pagamentoId).log("Pagamento {} passou para {}", pagamentoId, status);
    }

    @Override
    public void registrarWebhook(String assinatura) {
        registry.counter(PAGAMENTO_WEBHOOKS, "assinatura", assinatura).increment();
    }

    @Override
    public void registrarReconciliacao(String resultado) {
        registry.counter(PAGAMENTO_RECONCILIACOES, RESULTADO, resultado).increment();
    }

    @Override
    public void registrarEstorno(String resultado, UUID orcamentoId, String pagamentoGatewayId) {
        registry.counter(PAGAMENTO_ESTORNOS, RESULTADO, resultado).increment();
        var nivel = "sucesso".equals(resultado) ? log.atInfo() : log.atWarn();
        nivel.addKeyValue(ORCAMENTO_ID, orcamentoId)
            .addKeyValue("pagamento.gateway.id", pagamentoGatewayId)
            .log("Estorno {} do pagamento {} do orçamento {}", resultado, pagamentoGatewayId, orcamentoId);
    }

    @Override
    public void registrarIntegracaoMercadoPago(String operacao, String resultado) {
        registry.counter(INTEGRACAO_MERCADOPAGO, "operacao", operacao, RESULTADO, resultado).increment();
    }

    @Override
    public void registrarMensagemPublicada(String tipo) {
        registry.counter(MENSAGERIA_PUBLICADAS, "tipo", tipo).increment();
    }

    @Override
    public void registrarMensagemConsumida(String tipo, String resultado) {
        registry.counter(MENSAGERIA_CONSUMIDAS, "tipo", tipo, RESULTADO, resultado).increment();
    }

    @Override
    public void registrarFalha(String operacao, String referencia, Exception erro) {
        log.atWarn()
            .addKeyValue("operacao", operacao)
            .addKeyValue("referencia", referencia)
            .setCause(erro)
            .log("Falha em {} para {}: {}", operacao, referencia, erro.getMessage());
    }
}
