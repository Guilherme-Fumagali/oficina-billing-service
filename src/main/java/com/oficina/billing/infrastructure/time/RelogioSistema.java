package com.oficina.billing.infrastructure.time;

import com.oficina.billing.application.port.Relogio;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class RelogioSistema implements Relogio {

    private final Clock clock = Clock.systemUTC();

    @Override
    public Instant agora() {
        return clock.instant();
    }
}
