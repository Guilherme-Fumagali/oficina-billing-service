package com.oficina.billing.application.mensagem;

public record ComandoRejeitado(String comando, String motivo, String codigo) implements EventoBilling {}
