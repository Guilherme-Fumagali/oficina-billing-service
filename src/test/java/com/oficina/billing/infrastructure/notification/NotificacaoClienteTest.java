package com.oficina.billing.infrastructure.notification;

import com.oficina.billing.domain.valueobject.DecisaoOrcamento;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static com.oficina.billing.support.Fixtures.TOKEN;
import static com.oficina.billing.support.Fixtures.orcamento;
import static com.oficina.billing.support.Fixtures.orcamentoAprovado;
import static com.oficina.billing.support.Fixtures.pagamentoPendente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NotificacaoClienteTest {

    @Mock JavaMailSender mailSender;

    SmtpNotificacaoCliente smtp;

    @BeforeEach
    void setUp() {
        smtp = new SmtpNotificacaoCliente(mailSender, "oficina@example.com", "https://api.oficina.example");
    }

    @Test
    void emailDoOrcamentoTemItensTotalELinksDeDecisao() throws Exception {
        var orcamento = orcamento();
        var mime = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mime);

        smtp.enviarOrcamento(orcamento, TOKEN);

        verify(mailSender).send(mime);
        assertThat(mime.getAllRecipients()[0]).hasToString("maria@example.com");
        assertThat(mime.getFrom()[0]).hasToString("oficina@example.com");
        assertThat(mime.getSubject()).contains("ABC1D23");
        var html = (String) mime.getContent();
        assertThat(html)
            .contains("Troca de óleo", "Filtro de óleo", "210,00")
            .contains("https://api.oficina.example/api/orcamentos/" + orcamento.getId() + "/decisao?token=" + TOKEN
                + "&amp;decisao=APROVAR")
            .contains("decisao=REPROVAR");
    }

    @Test
    void linkDeDecisaoUsaAUrlPublica() {
        var orcamento = orcamento();

        assertThat(smtp.linkDecisao(orcamento, "abc-_123", DecisaoOrcamento.REPROVAR))
            .isEqualTo("https://api.oficina.example/api/orcamentos/" + orcamento.getId() + "/decisao?token=abc-_123&decisao=REPROVAR");
    }

    @Test
    void emailDoPagamentoTemOLinkDoMercadoPago() throws Exception {
        var orcamento = orcamentoAprovado();
        var pagamento = pagamentoPendente(orcamento);
        var mime = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mime);

        smtp.enviarLinkPagamento(orcamento, pagamento);

        var html = (String) mime.getContent();
        assertThat(html).contains("https://mp.example/checkout", "Pagar com Mercado Pago", "210,00");
    }

    @Test
    void canalDeLogNaoRegistraOToken(CapturedOutput saida) {
        var log = new LogNotificacaoCliente();
        var orcamento = orcamentoAprovado();

        log.enviarOrcamento(orcamento, TOKEN);
        log.enviarLinkPagamento(orcamento, pagamentoPendente(orcamento));

        assertThat(saida.getOut())
            .contains("Orçamento " + orcamento.getId())
            .contains("Pagamento do orçamento " + orcamento.getId())
            .doesNotContain(TOKEN);
    }
}
