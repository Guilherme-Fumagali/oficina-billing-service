package com.oficina.billing.application.port;

import java.util.UUID;

public interface MensagensProcessadas {

    boolean registrar(UUID mensagemId, String tipo);

    boolean jaProcessada(UUID mensagemId);
}
