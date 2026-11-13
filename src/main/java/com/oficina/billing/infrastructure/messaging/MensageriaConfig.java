package com.oficina.billing.infrastructure.messaging;

import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MensageriaConfig {

    @Bean
    public SqsMessagingMessageConverter conversorMensagensSqs() {
        var conversor = new SqsMessagingMessageConverter();
        conversor.setPayloadTypeMapper(mensagem -> null);
        conversor.doNotSendPayloadTypeHeader();
        return conversor;
    }
}
