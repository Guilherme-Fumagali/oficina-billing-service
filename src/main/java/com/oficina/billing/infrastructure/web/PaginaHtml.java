package com.oficina.billing.infrastructure.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

public final class PaginaHtml {

    public static final String VERDE = "#16a34a";
    public static final String VERMELHO = "#dc2626";
    public static final String AMBAR = "#d97706";
    public static final String AZUL = "#009ee3";

    private PaginaHtml() {}

    public static ResponseEntity<String> resposta(String corpo) {
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            .body(corpo);
    }

    public static String escapar(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor, "UTF-8");
    }

    public static String botao(String rotulo, String cor) {
        return """
            <button type="submit" style="border:0;border-radius:10px;padding:14px 28px;font-size:16px;color:#fff;background:%s;cursor:pointer;">%s</button>
            """.formatted(cor, escapar(rotulo));
    }

    public static String link(String rotulo, String href, String cor) {
        return """
            <a href="%s" style="display:inline-block;margin:8px;border-radius:10px;padding:14px 28px;font-size:16px;color:#fff;background:%s;text-decoration:none;">%s</a>
            """.formatted(escapar(href), cor, escapar(rotulo));
    }

    public static String pagina(String cor, String icone, String titulo, String texto, String conteudo) {
        return """
            <!doctype html>
            <html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="robots" content="noindex">
            <title>%s</title></head>
            <body style="margin:0;background:#f1f5f9;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
              <div style="max-width:480px;margin:48px auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,.08);">
                <div style="background:%s;height:8px;"></div>
                <div style="padding:40px 32px;text-align:center;">
                  <div style="width:72px;height:72px;line-height:72px;border-radius:50%%;background:%s;color:#fff;font-size:36px;margin:0 auto 20px;">%s</div>
                  <h1 style="margin:0 0 12px;font-size:22px;color:#0f172a;">%s</h1>
                  <p style="margin:0;font-size:15px;line-height:1.6;color:#475569;">%s</p>
                  %s
                </div>
                <div style="padding:16px;text-align:center;background:#f8fafc;font-size:12px;color:#94a3b8;">
                  Oficina Mecânica &middot; Tech Challenge
                </div>
              </div>
            </body></html>
            """.formatted(escapar(titulo), cor, cor, icone, escapar(titulo), escapar(texto), conteudo);
    }
}
