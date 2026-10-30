package com.oficina.billing.integracao;

import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.application.usecase.orcamento.ExpirarOrcamentosUseCase;
import com.oficina.billing.application.usecase.pagamento.ExpirarPagamentosUseCase;
import com.oficina.billing.application.usecase.pagamento.ReconciliarPagamentosUseCase;
import com.oficina.billing.domain.entity.DadosNovoOrcamento;
import com.oficina.billing.domain.entity.ItemOrcamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.CanalDecisao;
import com.oficina.billing.domain.valueobject.TipoItem;
import com.oficina.billing.domain.valueobject.TokenDecisao;
import com.oficina.billing.infrastructure.messaging.MonitorDlq;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AgendamentosIntegrationTest extends IntegracaoTestBase {

    @Autowired OrcamentoRepository orcamentos;
    @Autowired PagamentoRepository pagamentos;
    @Autowired UnidadeDeTrabalho unidadeDeTrabalho;
    @Autowired ExpirarOrcamentosUseCase expirarOrcamentos;
    @Autowired ExpirarPagamentosUseCase expirarPagamentos;
    @Autowired ReconciliarPagamentosUseCase reconciliarPagamentos;
    @Autowired MonitorDlq monitorDlq;
    @Autowired MeterRegistry registry;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void orcamentoVencidoExpiraEPublicaOrcamentoExpirado() {
        var orcamento = salvarOrcamento(Instant.now().minus(Duration.ofHours(200)), false);

        expirarOrcamentos.expirarOrcamentosVencidos();

        var expirado = aguardarEvento("OrcamentoExpirado", orcamento.getOsId());
        assertThat(expirado.dados().path("orcamentoId").asText()).isEqualTo(orcamento.getId().toString());
        assertThat(jdbc.queryForObject("SELECT status FROM orcamentos WHERE id = ?", String.class, orcamento.getId()))
            .isEqualTo("EXPIRADO");
    }

    @Test
    void reconciliacaoAplicaOPagamentoAprovadoEncontradoNaBusca() {
        var orcamento = salvarOrcamento(Instant.now().minus(Duration.ofMinutes(10)), true);
        var pagamento = salvarPagamento(orcamento, Instant.now().minus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofHours(1)));
        MERCADO_PAGO.stubFor(WireMock.get(urlPathEqualTo("/v1/payments/search"))
            .withQueryParam("external_reference", equalTo(orcamento.getId().toString()))
            .willReturn(WireMock.okJson("{\"results\":[%s]}".formatted(corpoPagamento("990001", "approved", orcamento.getId())))));

        reconciliarPagamentos.executar();

        var aprovado = aguardarEvento("PagamentoAprovado", orcamento.getOsId());
        assertThat(aprovado.dados().path("pagamentoId").asText()).isEqualTo(pagamento.getId().toString());
        assertThat(aprovado.dados().path("valor").decimalValue()).isEqualTo(new BigDecimal("210.00"));
        assertThat(registry.find("oficina.pagamento.reconciliacoes").tags("resultado", "aprovado").counter()).isNotNull();
    }

    @Test
    void pagamentoVencidoSemConfirmacaoExpiraEPublicaRecusaPorPrazo() {
        var orcamento = salvarOrcamento(Instant.now().minus(Duration.ofHours(3)), true);
        var pagamento = salvarPagamento(orcamento, Instant.now().minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(1)));
        MERCADO_PAGO.stubFor(WireMock.get(urlPathEqualTo("/v1/payments/search"))
            .withQueryParam("external_reference", equalTo(orcamento.getId().toString()))
            .willReturn(WireMock.okJson("{\"results\":[]}")));

        expirarPagamentos.executar();

        var recusado = aguardarEvento("PagamentoRecusado", orcamento.getOsId());
        assertThat(recusado.dados().path("motivo").asText()).isEqualTo("EXPIRADO");
        assertThat(jdbc.queryForObject("SELECT status FROM pagamentos WHERE id = ?", String.class, pagamento.getId()))
            .isEqualTo("EXPIRADO");
    }

    @Test
    void reservaDePagamentosPulaLinhasBloqueadasPorOutraReplica() throws Exception {
        var antigo = Instant.now().minus(Duration.ofMinutes(30));
        var bloqueado = salvarPagamento(salvarOrcamento(antigo, true), antigo, Instant.now().plus(Duration.ofHours(1)));
        var livre = salvarPagamento(salvarOrcamento(antigo, true), antigo, Instant.now().plus(Duration.ofHours(1)));
        var linhaBloqueada = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var outraReplica = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM pagamentos WHERE id = ? FOR UPDATE", bloqueado.getId());
                linhaBloqueada.countDown();
                aguardar(liberar);
            }));
            assertThat(linhaBloqueada.await(10, TimeUnit.SECONDS)).isTrue();

            var primeiraRodada = reservar();

            assertThat(primeiraRodada).contains(livre.getId()).doesNotContain(bloqueado.getId());
            liberar.countDown();
            outraReplica.get(10, TimeUnit.SECONDS);

            var segundaRodada = reservar();

            assertThat(segundaRodada).contains(bloqueado.getId()).doesNotContain(livre.getId());
        } finally {
            liberar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void monitorPublicaAQuantidadeDeMensagensNaDlq() {
        enviarCorpo(FILA_DLQ, "{\"mensagem\":\"envenenada\"}", "Desconhecido");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            monitorDlq.atualizar();
            assertThat(monitorDlq.mensagensNaDlq()).isPositive();
        });
        assertThat(registry.get("oficina.mensageria.dlq.mensagens").tag("fila", FILA_DLQ).gauge().value()).isPositive();
    }

    private List<UUID> reservar() {
        var agora = Instant.now();
        return unidadeDeTrabalho.executar(() -> pagamentos.reservarPendentesCriadosAte(
            agora.minus(Duration.ofSeconds(60)), agora, agora.plus(Duration.ofMinutes(2)), 500));
    }

    private Orcamento salvarOrcamento(Instant criadoEm, boolean aprovado) {
        var dados = new DadosNovoOrcamento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Maria Souza",
            "maria@example.com", "ABC1D23", "Corolla", List.of(
                ItemOrcamento.novo(TipoItem.SERVICO, UUID.randomUUID(), "Troca de óleo", 1, new BigDecimal("120.00")),
                ItemOrcamento.novo(TipoItem.PECA, UUID.randomUUID(), "Filtro de óleo", 2, new BigDecimal("45.00"))));
        var orcamento = Orcamento.criar(dados, TokenDecisao.calcularHash("token"), criadoEm, Duration.ofHours(168));
        if (aprovado) {
            orcamento.aprovar(CanalDecisao.BALCAO, criadoEm.plusSeconds(1));
        }
        return unidadeDeTrabalho.executar(() -> orcamentos.salvar(orcamento));
    }

    private Pagamento salvarPagamento(Orcamento orcamento, Instant criadoEm, Instant prazo) {
        var pagamento = Pagamento.criar(orcamento, "pref-" + orcamento.getId(), "https://mp/checkout", prazo, criadoEm);
        return unidadeDeTrabalho.executar(() -> pagamentos.salvar(pagamento));
    }

    private static void aguardar(CountDownLatch trava) {
        try {
            trava.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
