CREATE TABLE orcamentos (
    id                  UUID PRIMARY KEY,
    os_id               UUID           NOT NULL UNIQUE,
    saga_id             UUID           NOT NULL,
    cliente_id          UUID           NOT NULL,
    cliente_nome        VARCHAR(160)   NOT NULL,
    cliente_email       VARCHAR(160)   NOT NULL,
    veiculo_placa       VARCHAR(10)    NOT NULL,
    veiculo_modelo      VARCHAR(100)   NOT NULL,
    total               NUMERIC(12, 2) NOT NULL CHECK (total > 0),
    status              VARCHAR(30)    NOT NULL
        CHECK (status IN ('AGUARDANDO_APROVACAO', 'APROVADO', 'REPROVADO', 'EXPIRADO', 'CANCELADO')),
    criado_em           TIMESTAMP      NOT NULL,
    valido_ate          TIMESTAMP      NOT NULL,
    token_decisao_hash  VARCHAR(64)    NOT NULL,
    canal_decisao       VARCHAR(10)    CHECK (canal_decisao IN ('CLIENTE', 'LINK', 'BALCAO')),
    aprovado_em         TIMESTAMP,
    reprovado_em        TIMESTAMP,
    expirado_em         TIMESTAMP,
    cancelado_em        TIMESTAMP,
    motivo_cancelamento VARCHAR(200),
    versao              BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_orcamentos_cliente ON orcamentos (cliente_id);
CREATE INDEX idx_orcamentos_vencimento ON orcamentos (valido_ate) WHERE status = 'AGUARDANDO_APROVACAO';

CREATE TABLE itens_orcamento (
    id             UUID PRIMARY KEY,
    orcamento_id   UUID           NOT NULL REFERENCES orcamentos (id) ON DELETE CASCADE,
    tipo           VARCHAR(10)    NOT NULL CHECK (tipo IN ('SERVICO', 'PECA')),
    referencia_id  UUID           NOT NULL,
    descricao      VARCHAR(200)   NOT NULL,
    quantidade     INT            NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(12, 2) NOT NULL CHECK (preco_unitario > 0),
    subtotal       NUMERIC(12, 2) NOT NULL CHECK (subtotal > 0)
);

CREATE INDEX idx_itens_orcamento_orcamento ON itens_orcamento (orcamento_id);

CREATE TABLE pagamentos (
    id               UUID PRIMARY KEY,
    orcamento_id     UUID           NOT NULL UNIQUE REFERENCES orcamentos (id),
    os_id            UUID           NOT NULL,
    valor            NUMERIC(12, 2) NOT NULL CHECK (valor > 0),
    status           VARCHAR(20)    NOT NULL
        CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'ESTORNADO', 'EXPIRADO')),
    mp_preference_id VARCHAR(100),
    mp_payment_id    VARCHAR(40) UNIQUE,
    link_pagamento   VARCHAR(500)   NOT NULL,
    meio             VARCHAR(40),
    status_detalhe   VARCHAR(80),
    prazo            TIMESTAMP      NOT NULL,
    criado_em        TIMESTAMP      NOT NULL,
    aprovado_em      TIMESTAMP,
    estornado_em     TIMESTAMP,
    payload_mp       JSONB,
    reservado_ate    TIMESTAMP,
    versao           BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_pagamentos_status_criado ON pagamentos (status, criado_em);
CREATE INDEX idx_pagamentos_os ON pagamentos (os_id);

CREATE TABLE notificacoes_pagamento (
    id                UUID PRIMARY KEY,
    request_id        VARCHAR(100),
    data_id           VARCHAR(100),
    tipo              VARCHAR(60),
    assinatura_valida BOOLEAN   NOT NULL,
    recebida_em       TIMESTAMP NOT NULL
);

CREATE INDEX idx_notificacoes_pagamento_data ON notificacoes_pagamento (data_id);

CREATE TABLE outbox (
    id              UUID PRIMARY KEY,
    fila            VARCHAR(120) NOT NULL,
    tipo            VARCHAR(60)  NOT NULL,
    versao          INT          NOT NULL,
    os_id           UUID         NOT NULL,
    payload         JSONB        NOT NULL,
    cabecalhos      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    criada_em       TIMESTAMP    NOT NULL,
    publicada_em    TIMESTAMP,
    tentativas      INT          NOT NULL DEFAULT 0,
    ultimo_erro     VARCHAR(500)
);

CREATE INDEX idx_outbox_pendentes ON outbox (criada_em) WHERE publicada_em IS NULL;

CREATE TABLE mensagens_processadas (
    mensagem_id   UUID PRIMARY KEY,
    tipo          VARCHAR(60) NOT NULL,
    processada_em TIMESTAMP   NOT NULL
);
