package com.oficina.billing.application.usecase.pagamento;

import com.oficina.billing.application.comando.ComandoEstornarPagamento;
import com.oficina.billing.application.comando.ResultadoComando;
import com.oficina.billing.application.comando.TiposComando;
import com.oficina.billing.application.mensagem.PagamentoEstornado;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import com.oficina.billing.domain.valueobject.StatusPagamento;

import java.math.BigDecimal;
import java.util.UUID;

public class EstornarPagamentoUseCase {

    private static final BigDecimal VALOR_ZERO = new BigDecimal("0.00");

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final MensagensProcessadas mensagensProcessadas;
    private final PublicadorMensagens publicador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final MetricasBilling metricas;
    private final Relogio relogio;

    public EstornarPagamentoUseCase(OrcamentoRepository orcamentos,
                                    PagamentoRepository pagamentos,
                                    GatewayPagamento gateway,
                                    MensagensProcessadas mensagensProcessadas,
                                    PublicadorMensagens publicador,
                                    UnidadeDeTrabalho unidadeDeTrabalho,
                                    MetricasBilling metricas,
                                    Relogio relogio) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.mensagensProcessadas = mensagensProcessadas;
        this.publicador = publicador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    public ResultadoComando executar(ComandoEstornarPagamento comando) {
        if (mensagensProcessadas.jaProcessada(comando.mensagemId())) {
            return ResultadoComando.DUPLICADA;
        }
        pagamentos.buscarPorOsId(comando.osId())
            .filter(Pagamento::isAprovado)
            .ifPresent(this::estornarNoGateway);

        return unidadeDeTrabalho.executar(() -> {
            if (!mensagensProcessadas.registrar(comando.mensagemId(), TiposComando.ESTORNAR_PAGAMENTO)) {
                return ResultadoComando.DUPLICADA;
            }
            var orcamentoId = orcamentos.buscarPorOsId(comando.osId()).map(Orcamento::getId).orElse(null);
            var pagamento = pagamentos.buscarPorOsId(comando.osId()).orElse(null);
            publicador.publicar(comando.correlacao(), registrarEstorno(orcamentoId, pagamento));
            return ResultadoComando.APLICADA;
        });
    }

    private void estornarNoGateway(Pagamento pagamento) {
        try {
            gateway.estornar(pagamento.getMpPaymentId(), pagamento.getId().toString());
        } catch (RuntimeException e) {
            metricas.registrarEstorno("falha", pagamento.getOrcamentoId(), pagamento.getMpPaymentId());
            throw e;
        }
    }

    private PagamentoEstornado registrarEstorno(UUID orcamentoId, Pagamento pagamento) {
        var agora = relogio.agora();
        if (pagamento == null) {
            return new PagamentoEstornado(orcamentoId, null, VALOR_ZERO, agora);
        }
        if (pagamento.isAprovado()) {
            pagamento.estornar(agora);
            var salvo = pagamentos.salvar(pagamento);
            metricas.registrarEstorno("sucesso", salvo.getOrcamentoId(), salvo.getMpPaymentId());
            metricas.registrarResultadoPagamento(salvo.getId(), StatusPagamento.ESTORNADO);
            return new PagamentoEstornado(orcamentoId, salvo.getId(), salvo.getValor(), salvo.getEstornadoEm());
        }
        if (pagamento.getStatus() == StatusPagamento.ESTORNADO) {
            return new PagamentoEstornado(orcamentoId, pagamento.getId(), pagamento.getValor(), pagamento.getEstornadoEm());
        }
        return new PagamentoEstornado(orcamentoId, pagamento.getId(), VALOR_ZERO, agora);
    }
}
