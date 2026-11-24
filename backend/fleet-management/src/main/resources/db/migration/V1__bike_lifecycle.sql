-- Ciclo de vida das bicicletas (RF06): estado atual + histórico de transições.
-- Os dados de registo (número de série, IMEI, modelo, hub...) entram numa migração seguinte,
-- com ALTER TABLE bikes ADD COLUMN ..., pelos endpoints de registo individual.

CREATE TABLE bikes (
    bike_id          VARCHAR(16)  PRIMARY KEY,
    state            VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    state_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT bikes_bike_id_format CHECK (bike_id ~ '^MB-[0-9]{4}-[0-9]{3}$'),
    CONSTRAINT bikes_state_valid    CHECK (state IN ('AVAILABLE', 'IN_USE', 'IN_MAINTENANCE', 'RETIRED'))
);

CREATE TABLE bike_state_transitions (
    id          UUID         PRIMARY KEY,
    bike_id     VARCHAR(16)  NOT NULL REFERENCES bikes (bike_id),
    from_state  VARCHAR(20)  NOT NULL,
    to_state    VARCHAR(20)  NOT NULL,
    event       VARCHAR(32)  NOT NULL,
    actor_role  VARCHAR(32)  NOT NULL,
    actor_id    VARCHAR(64),
    reason      VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT transitions_from_valid CHECK (from_state IN ('AVAILABLE', 'IN_USE', 'IN_MAINTENANCE', 'RETIRED')),
    CONSTRAINT transitions_to_valid   CHECK (to_state   IN ('AVAILABLE', 'IN_USE', 'IN_MAINTENANCE', 'RETIRED')),
    CONSTRAINT transitions_changes_state CHECK (from_state <> to_state)
);

CREATE INDEX idx_transitions_bike_time ON bike_state_transitions (bike_id, occurred_at DESC);

-- Bicicletas de exemplo do simulador, para testar sem os endpoints de registo.
-- Apagar esta parte quando o registo estiver ligado.
INSERT INTO bikes (bike_id) VALUES
    ('MB-2026-001'), ('MB-2026-002'), ('MB-2026-003'), ('MB-2026-004'), ('MB-2026-005');
