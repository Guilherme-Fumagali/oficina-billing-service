package com.oficina.billing.infrastructure.notification;

import com.oficina.billing.application.port.NotificacaoCliente;
import com.oficina.billing.domain.entity.ItemOrcamento;
import com.oficina.billing.domain.entity.Orcamento;
import com.oficina.billing.domain.entity.Pagamento;
import com.oficina.billing.domain.valueobject.DecisaoOrcamento;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.notificacao", name = "canal", havingValue = "smtp")
public class SmtpNotificacaoCliente implements NotificacaoCliente {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter FORMATO_DATA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final JavaMailSender mailSender;
    private final String remetente;
    private final String urlPublica;

    public SmtpNotificacaoCliente(JavaMailSender mailSender,
                                  @Value("${app.mail.from}") String remetente,
                                  @Value("${app.url-publica}") String urlPublica) {
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.urlPublica = urlPublica;
    }

    @Override
    public void enviarOrcamento(Orcamento orcamento, String tokenDecisao) {
        var aprovar = linkDecisao(orcamento, tokenDecisao, DecisaoOrcamento.APROVAR);
        var reprovar = linkDecisao(orcamento, tokenDecisao, DecisaoOrcamento.REPROVAR);
        enviar(orcamento.getClienteEmail(),
            "Orçamento do veículo %s".formatted(orcamento.getVeiculoPlaca()),
            corpoOrcamento(orcamento, aprovar, reprovar));
    }

    @Override
    public void enviarLinkPagamento(Orcamento orcamento, Pagamento pagamento) {
        enviar(orcamento.getClienteEmail(),
            "Pagamento do orçamento do veículo %s".formatted(orcamento.getVeiculoPlaca()),
            corpoPagamento(orcamento, pagamento));
    }

    String linkDecisao(Orcamento orcamento, String token, DecisaoOrcamento decisao) {
        return UriComponentsBuilder.fromUriString(urlPublica)
            .path("/api/orcamentos/{id}/decisao")
            .queryParam("token", token)
            .queryParam("decisao", decisao.name())
            .encode()
            .buildAndExpand(orcamento.getId())
            .toUriString();
    }

    private void enviar(String destinatario, String assunto, String html) {
        try {
            var mensagem = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mensagem, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(html, true);
            mailSender.send(mensagem);
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao montar o e-mail para o cliente.", e);
        }
    }

    private String corpoOrcamento(Orcamento orcamento, String linkAprovar, String linkReprovar) {
        var linhas = orcamento.getItens().stream().map(this::linhaItem).collect(Collectors.joining());
        var conteudo = """
            <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#475569;">
              O orçamento do veículo <strong>%s</strong> (%s) está pronto. Confira os itens e escolha uma opção.
            </p>
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px;color:#0f172a;border-collapse:collapse;">
              <tr style="background:#f8fafc;color:#64748b;text-align:left;">
                <th style="padding:8px;">Item</th><th style="padding:8px;">Qtd.</th>
                <th style="padding:8px;">Unitário</th><th style="padding:8px;">Subtotal</th>
              </tr>
              %s
            </table>
            <p style="margin:20px 0 4px;font-size:22px;font-weight:800;color:#0f172a;">Total: R$ %s</p>
            <p style="margin:0 0 24px;font-size:13px;color:#64748b;">Válido até %s.</p>
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
              <tr>
                <td width="50%%" style="padding-right:6px;">
                  <a href="%s" style="display:block;text-align:center;background:#16a34a;color:#fff;text-decoration:none;font-weight:700;padding:14px 0;border-radius:10px;">Aprovar</a>
                </td>
                <td width="50%%" style="padding-left:6px;">
                  <a href="%s" style="display:block;text-align:center;background:#dc2626;color:#fff;text-decoration:none;font-weight:700;padding:14px 0;border-radius:10px;">Reprovar</a>
                </td>
              </tr>
            </table>
            <p style="margin:24px 0 0;font-size:12px;line-height:1.6;color:#94a3b8;">
              Os links abrem uma página de confirmação; a decisão só é registrada depois da confirmação.
            </p>
            """.formatted(
                escapar(orcamento.getVeiculoModelo()), escapar(orcamento.getVeiculoPlaca()), linhas,
                moeda(orcamento.getTotal()), data(orcamento.getValidoAte()),
                escapar(linkAprovar), escapar(linkReprovar));
        return moldura(orcamento.getClienteNome(), conteudo);
    }

    private String corpoPagamento(Orcamento orcamento, Pagamento pagamento) {
        var conteudo = """
            <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#475569;">
              Recebemos a aprovação do orçamento do veículo <strong>%s</strong>. O pagamento de
              <strong>R$ %s</strong> pode ser feito pelo Mercado Pago até %s.
            </p>
            <a href="%s" style="display:block;text-align:center;background:#009ee3;color:#fff;text-decoration:none;font-weight:700;padding:14px 0;border-radius:10px;">Pagar com Mercado Pago</a>
            <p style="margin:24px 0 0;font-size:12px;line-height:1.6;color:#94a3b8;">
              O serviço começa assim que o pagamento for confirmado.
            </p>
            """.formatted(escapar(orcamento.getVeiculoPlaca()), moeda(pagamento.getValor()),
                data(pagamento.getPrazo()), escapar(pagamento.getLinkPagamento()));
        return moldura(orcamento.getClienteNome(), conteudo);
    }

    private String linhaItem(ItemOrcamento item) {
        return """
            <tr style="border-top:1px solid #e2e8f0;">
              <td style="padding:8px;">%s</td><td style="padding:8px;">%d</td>
              <td style="padding:8px;">R$ %s</td><td style="padding:8px;">R$ %s</td>
            </tr>
            """.formatted(escapar(item.getDescricao()), item.getQuantidade(),
                moeda(item.getPrecoUnitario()), moeda(item.getSubtotal()));
    }

    private String moldura(String nome, String conteudo) {
        return """
            <!doctype html>
            <html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;background:#f1f5f9;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;background:#fff;border-radius:16px;overflow:hidden;">
                    <tr><td style="background:#0f172a;padding:24px 32px;">
                      <span style="color:#fff;font-size:18px;font-weight:700;">Oficina Mecânica</span>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="margin:0 0 8px;font-size:16px;color:#0f172a;">Olá, %s!</p>
                      %s
                    </td></tr>
                    <tr><td style="padding:16px;text-align:center;background:#f8fafc;font-size:12px;color:#94a3b8;">
                      Oficina Mecânica &middot; Tech Challenge
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(escapar(nome), conteudo);
    }

    private static String escapar(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor, "UTF-8");
    }

    private static String moeda(BigDecimal valor) {
        return String.format(PT_BR, "%,.2f", valor);
    }

    private static String data(Instant instante) {
        return FORMATO_DATA.format(instante);
    }
}
