package com.oficina.billing.infrastructure.observability;

import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MicrometerMetricasBillingTest {

    private SimpleMeterRegistry registry;
    private MicrometerMetricasBilling metricas;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricas = new MicrometerMetricasBilling(registry);
    }

    @Test
    void orcamentosGeradosEDecisoesPorResultadoECanal() {
        metricas.registrarOrcamentoGerado(UUID.randomUUID());
        metricas.registrarAprovacao(UUID.randomUUID(), CanalDecisao.LINK, new BigDecimal("210.00"));
        metricas.registrarAprovacao(UUID.randomUUID(), CanalDecisao.LINK, new BigDecimal("90.00"));
        metricas.registrarReprovacao(UUID.randomUUID(), CanalDecisao.CLIENTE);
        metricas.registrarOrcamentoExpirado(UUID.randomUUID());

        assertThat(registry.counter("oficina.orcamento.gerados").count()).isEqualTo(1);
        assertThat(registry.counter("oficina.orcamento.decisoes", "resultado", "aprovado", "canal", "LINK").count()).isEqualTo(2);
        assertThat(registry.counter("oficina.orcamento.decisoes", "resultado", "reprovado", "canal", "CLIENTE").count()).isEqualTo(1);
        assertThat(registry.counter("oficina.orcamento.decisoes", "resultado", "expirado", "canal", "SISTEMA").count()).isEqualTo(1);
        var valor = registry.find("oficina.orcamento.valor_aprovado").summary();
        assertThat(valor).isNotNull();
        assertThat(valor.count()).isEqualTo(2);
        assertThat(valor.totalAmount()).isEqualTo(300.0);
    }

    @Test
    void pagamentosEIntegracao() {
        metricas.registrarResultadoPagamento(UUID.randomUUID(), StatusPagamento.APROVADO);
        metricas.registrarIntegracaoMercadoPago("estornar", "falha");

        assertThat(registry.counter("oficina.pagamento.resultados", "status", "APROVADO").count()).isEqualTo(1);
        assertThat(registry.counter("oficina.integracao.mercadopago", "operacao", "estornar", "resultado", "falha").count()).isEqualTo(1);
    }

    @Test
    void mensageriaPorTipoEResultado() {
        metricas.registrarMensagemPublicada("OrcamentoGerado");
        metricas.registrarMensagemConsumida("GerarOrcamento", "duplicada");

        assertThat(registry.counter("oficina.mensageria.publicadas", "tipo", "OrcamentoGerado").count()).isEqualTo(1);
        assertThat(registry.counter("oficina.mensageria.consumidas", "tipo", "GerarOrcamento", "resultado", "duplicada").count())
            .isEqualTo(1);
    }

    @Test
    void falhasVaoParaOLogComOperacaoEReferencia(CapturedOutput saida) {
        metricas.registrarFalha("reconciliacao", "pagamento-1", new IllegalStateException("gateway fora"));

        assertThat(saida.getOut()).contains("Falha em reconciliacao para pagamento-1: gateway fora");
    }
}
