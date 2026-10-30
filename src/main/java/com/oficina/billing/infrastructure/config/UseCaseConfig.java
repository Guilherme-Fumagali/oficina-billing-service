package com.oficina.billing.infrastructure.config;

import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.MetricasBilling;
import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.application.port.PublicadorMensagens;
import com.oficina.billing.application.port.Relogio;
import com.oficina.billing.application.port.UnidadeDeTrabalho;
import com.oficina.billing.application.usecase.comando.RejeitarComandoUseCase;
import com.oficina.billing.application.usecase.orcamento.AprovarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.CancelarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.ConsultarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.DecidirPorTokenUseCase;
import com.oficina.billing.application.usecase.orcamento.ExpirarOrcamentosUseCase;
import com.oficina.billing.application.usecase.orcamento.GerarOrcamentoUseCase;
import com.oficina.billing.application.usecase.orcamento.ReprovarOrcamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.ConsultarPagamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.EstornarPagamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.ExpirarPagamentosUseCase;
import com.oficina.billing.application.usecase.pagamento.ParametrosReconciliacao;
import com.oficina.billing.application.usecase.pagamento.ProcessadorResultadoPagamento;
import com.oficina.billing.application.usecase.pagamento.ProcessarNotificacaoPagamentoUseCase;
import com.oficina.billing.application.usecase.pagamento.ReconciliarPagamentosUseCase;
import com.oficina.billing.domain.repository.NotificacaoPagamentoRepository;
import com.oficina.billing.domain.repository.OrcamentoRepository;
import com.oficina.billing.domain.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class UseCaseConfig {

    @Bean
    public GerarOrcamentoUseCase gerarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                                       MensagensProcessadas mensagens,
                                                       PublicadorMensagens publicador,
                                                       UnidadeDeTrabalho unidadeDeTrabalho,
                                                       NotificacaoCliente notificacao,
                                                       MetricasBilling metricas,
                                                       Relogio relogio,
                                                       @Value("${app.orcamento.validade-horas}") long validadeHoras) {
        return new GerarOrcamentoUseCase(orcamentos, mensagens, publicador, unidadeDeTrabalho, notificacao,
            metricas, relogio, Duration.ofHours(validadeHoras));
    }

    @Bean
    public AprovarOrcamentoUseCase aprovarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                                           PagamentoRepository pagamentos,
                                                           GatewayPagamento gateway,
                                                           PublicadorMensagens publicador,
                                                           UnidadeDeTrabalho unidadeDeTrabalho,
                                                           NotificacaoCliente notificacao,
                                                           MetricasBilling metricas,
                                                           Relogio relogio,
                                                           @Value("${app.pagamento.prazo-horas}") long prazoHoras) {
        return new AprovarOrcamentoUseCase(orcamentos, pagamentos, gateway, publicador, unidadeDeTrabalho,
            notificacao, metricas, relogio, Duration.ofHours(prazoHoras));
    }

    @Bean
    public ReprovarOrcamentoUseCase reprovarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                                             PublicadorMensagens publicador,
                                                             UnidadeDeTrabalho unidadeDeTrabalho,
                                                             MetricasBilling metricas,
                                                             Relogio relogio) {
        return new ReprovarOrcamentoUseCase(orcamentos, publicador, unidadeDeTrabalho, metricas, relogio);
    }

    @Bean
    public DecidirPorTokenUseCase decidirPorTokenUseCase(OrcamentoRepository orcamentos,
                                                         AprovarOrcamentoUseCase aprovar,
                                                         ReprovarOrcamentoUseCase reprovar) {
        return new DecidirPorTokenUseCase(orcamentos, aprovar, reprovar);
    }

    @Bean
    public CancelarOrcamentoUseCase cancelarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                                             MensagensProcessadas mensagens,
                                                             PublicadorMensagens publicador,
                                                             UnidadeDeTrabalho unidadeDeTrabalho,
                                                             Relogio relogio) {
        return new CancelarOrcamentoUseCase(orcamentos, mensagens, publicador, unidadeDeTrabalho, relogio);
    }

    @Bean
    public ConsultarOrcamentoUseCase consultarOrcamentoUseCase(OrcamentoRepository orcamentos,
                                                               PagamentoRepository pagamentos) {
        return new ConsultarOrcamentoUseCase(orcamentos, pagamentos);
    }

    @Bean
    public ConsultarPagamentoUseCase consultarPagamentoUseCase(PagamentoRepository pagamentos,
                                                               OrcamentoRepository orcamentos) {
        return new ConsultarPagamentoUseCase(pagamentos, orcamentos);
    }

    @Bean
    public ProcessadorResultadoPagamento processadorResultadoPagamento(OrcamentoRepository orcamentos,
                                                                       PagamentoRepository pagamentos,
                                                                       GatewayPagamento gateway,
                                                                       PublicadorMensagens publicador,
                                                                       UnidadeDeTrabalho unidadeDeTrabalho,
                                                                       MetricasBilling metricas,
                                                                       Relogio relogio,
                                                                       @Value("${app.pagamento.recusa-encerra}") boolean recusaEncerra) {
        return new ProcessadorResultadoPagamento(orcamentos, pagamentos, gateway, publicador, unidadeDeTrabalho,
            metricas, relogio, recusaEncerra);
    }

    @Bean
    public ProcessarNotificacaoPagamentoUseCase processarNotificacaoPagamentoUseCase(GatewayPagamento gateway,
                                                                                     ProcessadorResultadoPagamento processador,
                                                                                     NotificacaoPagamentoRepository notificacoes,
                                                                                     UnidadeDeTrabalho unidadeDeTrabalho,
                                                                                     Relogio relogio) {
        return new ProcessarNotificacaoPagamentoUseCase(gateway, processador, notificacoes, unidadeDeTrabalho, relogio);
    }

    @Bean
    public ReconciliarPagamentosUseCase reconciliarPagamentosUseCase(PagamentoRepository pagamentos,
                                                                     GatewayPagamento gateway,
                                                                     ProcessadorResultadoPagamento processador,
                                                                     UnidadeDeTrabalho unidadeDeTrabalho,
                                                                     MetricasBilling metricas,
                                                                     Relogio relogio,
                                                                     @Value("${app.pagamento.reconciliacao.idade-minima}") Duration idadeMinima,
                                                                     @Value("${app.pagamento.reconciliacao.reserva}") Duration reserva,
                                                                     @Value("${app.pagamento.reconciliacao.lote}") int lote) {
        return new ReconciliarPagamentosUseCase(pagamentos, gateway, processador, unidadeDeTrabalho, metricas, relogio,
            new ParametrosReconciliacao(idadeMinima, reserva, lote));
    }

    @Bean
    public ExpirarPagamentosUseCase expirarPagamentosUseCase(PagamentoRepository pagamentos,
                                                             GatewayPagamento gateway,
                                                             ProcessadorResultadoPagamento processador,
                                                             UnidadeDeTrabalho unidadeDeTrabalho,
                                                             MetricasBilling metricas,
                                                             Relogio relogio,
                                                             @Value("${app.pagamento.reconciliacao.reserva}") Duration reserva,
                                                             @Value("${app.pagamento.reconciliacao.lote}") int lote) {
        return new ExpirarPagamentosUseCase(pagamentos, gateway, processador, unidadeDeTrabalho, metricas, relogio,
            new ParametrosReconciliacao(Duration.ZERO, reserva, lote));
    }

    @Bean
    public ExpirarOrcamentosUseCase expirarOrcamentosUseCase(OrcamentoRepository orcamentos,
                                                             PublicadorMensagens publicador,
                                                             UnidadeDeTrabalho unidadeDeTrabalho,
                                                             MetricasBilling metricas,
                                                             Relogio relogio,
                                                             ExpirarPagamentosUseCase expirarPagamentos,
                                                             @Value("${app.orcamento.expiracao.lote}") int lote) {
        return new ExpirarOrcamentosUseCase(orcamentos, publicador, unidadeDeTrabalho, metricas, relogio,
            expirarPagamentos, lote);
    }

    @Bean
    public EstornarPagamentoUseCase estornarPagamentoUseCase(OrcamentoRepository orcamentos,
                                                             PagamentoRepository pagamentos,
                                                             GatewayPagamento gateway,
                                                             MensagensProcessadas mensagens,
                                                             PublicadorMensagens publicador,
                                                             UnidadeDeTrabalho unidadeDeTrabalho,
                                                             MetricasBilling metricas,
                                                             Relogio relogio) {
        return new EstornarPagamentoUseCase(orcamentos, pagamentos, gateway, mensagens, publicador,
            unidadeDeTrabalho, metricas, relogio);
    }

    @Bean
    public RejeitarComandoUseCase rejeitarComandoUseCase(MensagensProcessadas mensagens,
                                                         PublicadorMensagens publicador,
                                                         UnidadeDeTrabalho unidadeDeTrabalho) {
        return new RejeitarComandoUseCase(mensagens, publicador, unidadeDeTrabalho);
    }
}
