package com.oficina.billing.infrastructure.persistence.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class DataHora {

    private DataHora() {}

    public static LocalDateTime utc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    public static Instant instante(LocalDateTime dataHora) {
        return dataHora == null ? null : dataHora.toInstant(ZoneOffset.UTC);
    }
}
