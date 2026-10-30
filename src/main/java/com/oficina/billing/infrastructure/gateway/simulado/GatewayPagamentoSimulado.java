package com.oficina.billing.infrastructure.gateway.simulado;

import com.oficina.billing.application.exception.GatewayPagamentoException;
import com.oficina.billing.application.port.CobrancaCriada;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.PagamentoGateway;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.SolicitacaoCobranca;
import com.oficina.billing.domain.valueobject.StatusPagamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("local")
public class GatewayPagamentoSimulado implements GatewayPagamento {

    static final String CAMINHO_SIMULACAO = "/api/pagamentos/simulacao/";

    private final Map<String, PagamentoGateway> pagamentos = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong(1_000_000);
    private final Relogio relogio;
    private final String urlPublica;

    public GatewayPagamentoSimulado(Relogio relogio, @Value("${app.url-publica}") String urlPublica) {
        this.relogio = relogio;
        this.urlPublica = urlPublica;
    }

    @Override
    public CobrancaCriada criarCobranca(SolicitacaoCobranca solicitacao) {
        return new CobrancaCriada("simulada-" + solicitacao.orcamentoId(),
            urlPublica + CAMINHO_SIMULACAO + solicitacao.orcamentoId());
    }

    public String simular(UUID orcamentoId, boolean aprovado) {
        var id = String.valueOf(sequencia.incrementAndGet());
        var status = aprovado ? StatusPagamento.APROVADO : StatusPagamento.RECUSADO;
        var detalhe = aprovado ? "accredited" : "cc_rejected_other_reason";
        pagamentos.put(id, new PagamentoGateway(id, status, detalhe, orcamentoId.toString(), "simulado",
            aprovado ? relogio.agora() : null,
            "{\"id\":%s,\"status\":\"%s\",\"simulado\":true}".formatted(id, aprovado ? "approved" : "rejected")));
        return id;
    }

    @Override
    public PagamentoGateway consultarPagamento(String pagamentoGatewayId) {
        var pagamento = pagamentos.get(pagamentoGatewayId);
        if (pagamento == null) {
            throw new GatewayPagamentoException("Pagamento simulado não encontrado: " + pagamentoGatewayId, null);
        }
        return pagamento;
    }

    @Override
    public Optional<PagamentoGateway> buscarPorOrcamento(UUID orcamentoId) {
        var doOrcamento = pagamentos.values().stream()
            .filter(p -> orcamentoId.toString().equals(p.referenciaExterna()))
            .sorted(Comparator.comparing((PagamentoGateway p) -> Long.parseLong(p.id())).reversed())
            .toList();
        return doOrcamento.stream()
            .filter(p -> p.status() == StatusPagamento.APROVADO)
            .findFirst()
            .or(() -> doOrcamento.stream().findFirst());
    }

    @Override
    public void estornar(String pagamentoGatewayId, String chaveIdempotencia) {
        pagamentos.computeIfPresent(pagamentoGatewayId, (id, p) -> new PagamentoGateway(
            p.id(), StatusPagamento.ESTORNADO, "refunded", p.referenciaExterna(), p.meio(), p.aprovadoEm(), p.payload()));
    }
}
