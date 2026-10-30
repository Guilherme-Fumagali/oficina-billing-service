package com.oficina.billing.infrastructure.messaging;

import com.oficina.billing.application.port.MensagensProcessadas;
import com.oficina.billing.application.port.Relogio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.oficina.billing.infrastructure.persistence.mapper.DataHora.utc;

@Component
public class MensagensProcessadasJdbc implements MensagensProcessadas {

    private final JdbcTemplate jdbc;
    private final Relogio relogio;

    public MensagensProcessadasJdbc(JdbcTemplate jdbc, Relogio relogio) {
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    @Override
    public boolean registrar(UUID mensagemId, String tipo) {
        return jdbc.update("""
            INSERT INTO mensagens_processadas (mensagem_id, tipo, processada_em) VALUES (?, ?, ?)
            ON CONFLICT (mensagem_id) DO NOTHING
            """, mensagemId, tipo, utc(relogio.agora())) == 1;
    }

    @Override
    public boolean jaProcessada(UUID mensagemId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM mensagens_processadas WHERE mensagem_id = ?)", Boolean.class, mensagemId));
    }
}
