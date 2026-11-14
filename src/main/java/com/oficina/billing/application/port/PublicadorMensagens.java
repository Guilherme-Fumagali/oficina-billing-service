package com.oficina.billing.application.port;

import com.oficina.billing.application.mensagem.Correlacao;
import com.oficina.billing.application.mensagem.EventoBilling;

public interface PublicadorMensagens {

    void publicar(Correlacao correlacao, EventoBilling evento);
}
