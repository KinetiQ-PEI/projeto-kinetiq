-- Modelo de dados operacional da frota (PostgreSQL 17 + PostGIS 3.5).
-- Continua a V1 (bikes + bike_state_transitions). As colunas de registo da bicicleta
-- (número de série, IMEI, modelo, hub, pool) entram numa migração V3, com os endpoints de registo.
--
-- Convenções:
--   * coordenadas em WGS 84 (SRID 4326), ordem longitude/latitude, como o GPS envia;
--   * datas em TIMESTAMPTZ (UTC);
--   * enumerados como VARCHAR + CHECK, com os mesmos valores dos schemas em docs/schemas/;
--   * pessoas (rider, técnico) identificadas só pelo id do Keycloak: nomes e emails ficam no Keycloak (GDPR).

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- Zonas: área de operação, hubs e zonas proibidas (geofencing, RNF12, alertas UNAUTHORIZED_ZONE)
-- ---------------------------------------------------------------------------
CREATE TABLE zones (
    zone_id    INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(32)  NOT NULL UNIQUE,
    name       VARCHAR(80)  NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    area       geometry(Polygon, 4326) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT zones_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT zones_kind_valid  CHECK (kind IN ('OPERATING_AREA', 'HUB', 'RESTRICTED')),
    CONSTRAINT zones_area_valid  CHECK (ST_IsValid(area))
);
CREATE INDEX idx_zones_area ON zones USING GIST (area);

-- ---------------------------------------------------------------------------
-- Última posição conhecida de cada bicicleta (o histórico completo fica no InfluxDB).
-- Atualizada pelo Fleet Management Service; serve o mapa ao vivo e as consultas "bikes nesta zona".
-- ---------------------------------------------------------------------------
CREATE TABLE bike_positions (
    bike_id       VARCHAR(16)  PRIMARY KEY REFERENCES bikes (bike_id),
    location      geometry(Point, 4326) NOT NULL,
    speed_kmh     REAL,
    battery_level SMALLINT,
    odometer_km   DOUBLE PRECISION,
    zone_id       INTEGER      REFERENCES zones (zone_id),
    recorded_at   TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT positions_speed_range   CHECK (speed_kmh IS NULL OR speed_kmh BETWEEN 0 AND 60),
    CONSTRAINT positions_battery_range CHECK (battery_level IS NULL OR battery_level BETWEEN 0 AND 100),
    CONSTRAINT positions_odometer_pos  CHECK (odometer_km IS NULL OR odometer_km >= 0)
);
CREATE INDEX idx_bike_positions_location ON bike_positions USING GIST (location);
CREATE INDEX idx_bike_positions_zone ON bike_positions (zone_id);

-- ---------------------------------------------------------------------------
-- Alertas (tópico fleet.alerts, contrato docs/schemas/alert-schema.json)
-- ---------------------------------------------------------------------------
CREATE TABLE alerts (
    alert_id        UUID         PRIMARY KEY,           -- = alertId do evento: reenvios não duplicam
    bike_id         VARCHAR(16)  NOT NULL REFERENCES bikes (bike_id),
    alert_type      VARCHAR(32)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    zone_code       VARCHAR(32)  REFERENCES zones (code),
    details         TEXT,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT alerts_type_valid     CHECK (alert_type IN ('LOW_BATTERY', 'UNAUTHORIZED_ZONE', 'MAINTENANCE_REQUIRED', 'CONNECTION_LOST')),
    CONSTRAINT alerts_severity_valid CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT alerts_zone_when_zone CHECK (alert_type <> 'UNAUTHORIZED_ZONE' OR zone_code IS NOT NULL)
);
CREATE INDEX idx_alerts_bike_time ON alerts (bike_id, occurred_at DESC);
CREATE INDEX idx_alerts_open ON alerts (severity, occurred_at DESC) WHERE acknowledged_at IS NULL;

-- ---------------------------------------------------------------------------
-- Tickets de manutenção (UC05–UC07). Abrir um ticket põe a bicicleta em IN_MAINTENANCE
-- (evento SEND_TO_MAINTENANCE da máquina de estados).
-- ---------------------------------------------------------------------------
CREATE SEQUENCE maintenance_ticket_code_seq;

CREATE TABLE maintenance_tickets (
    ticket_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(12)  NOT NULL UNIQUE
                                  DEFAULT 'T-' || lpad(nextval('maintenance_ticket_code_seq')::text, 4, '0'),
    bike_id          VARCHAR(16)  NOT NULL REFERENCES bikes (bike_id),
    alert_id         UUID         REFERENCES alerts (alert_id),
    source           VARCHAR(20)  NOT NULL,
    severity         VARCHAR(10)  NOT NULL,
    title            VARCHAR(160) NOT NULL,
    details          TEXT,
    status           VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    assigned_to      VARCHAR(64),                       -- id do técnico no Keycloak
    resolution       VARCHAR(10),
    resolution_notes TEXT,
    opened_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    claimed_at       TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    CONSTRAINT tickets_source_valid     CHECK (source IN ('ML_ANOMALY', 'RULE', 'RIDER_REPORT', 'OPERATOR')),
    CONSTRAINT tickets_severity_valid   CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT tickets_status_valid     CHECK (status IN ('OPEN', 'CLAIMED', 'RESOLVED')),
    CONSTRAINT tickets_resolution_valid CHECK (resolution IS NULL OR resolution IN ('REPAIRED', 'RETIRED')),
    -- cada estado do ticket tem os campos que lhe correspondem
    CONSTRAINT tickets_status_fields CHECK (
        (status = 'OPEN'     AND assigned_to IS NULL     AND claimed_at IS NULL     AND resolved_at IS NULL     AND resolution IS NULL) OR
        (status = 'CLAIMED'  AND assigned_to IS NOT NULL AND claimed_at IS NOT NULL AND resolved_at IS NULL     AND resolution IS NULL) OR
        (status = 'RESOLVED' AND assigned_to IS NOT NULL AND claimed_at IS NOT NULL AND resolved_at IS NOT NULL AND resolution IS NOT NULL)
    ),
    CONSTRAINT tickets_time_order CHECK (
        (claimed_at IS NULL OR claimed_at >= opened_at) AND
        (resolved_at IS NULL OR resolved_at >= claimed_at)
    )
);
CREATE INDEX idx_tickets_status ON maintenance_tickets (status, opened_at DESC);
CREATE INDEX idx_tickets_bike ON maintenance_tickets (bike_id, opened_at DESC);
CREATE INDEX idx_tickets_assignee ON maintenance_tickets (assigned_to) WHERE status = 'CLAIMED';

-- ---------------------------------------------------------------------------
-- Viagens (UC12, CO₂, analytics). Privacidade (slide "Privacy by design"):
--   * o rider é só o id do Keycloak (pseudónimo);
--   * a rota é simplificada e sem os primeiros e últimos 200 m (calculado antes de gravar);
--   * apagadas 90 dias depois do fim (purge_expired_trips).
-- ---------------------------------------------------------------------------
CREATE TABLE trips (
    trip_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    bike_id       VARCHAR(16)   NOT NULL REFERENCES bikes (bike_id),
    rider_id      VARCHAR(64)   NOT NULL,
    started_at    TIMESTAMPTZ   NOT NULL,
    ended_at      TIMESTAMPTZ,
    start_zone_id INTEGER       REFERENCES zones (zone_id),
    end_zone_id   INTEGER       REFERENCES zones (zone_id),
    distance_km   NUMERIC(8, 3),
    battery_start SMALLINT,
    battery_end   SMALLINT,
    energy_wh     REAL,
    co2_saved_g   REAL,                                  -- preenchido pelo módulo de CO₂
    route         geometry(LineString, 4326),
    CONSTRAINT trips_time_order     CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT trips_distance_pos   CHECK (distance_km IS NULL OR distance_km >= 0),
    CONSTRAINT trips_battery_range  CHECK ((battery_start IS NULL OR battery_start BETWEEN 0 AND 100)
                                       AND (battery_end   IS NULL OR battery_end   BETWEEN 0 AND 100)),
    CONSTRAINT trips_closed_fields  CHECK (ended_at IS NOT NULL OR (distance_km IS NULL AND route IS NULL))
);
-- No máximo uma viagem em curso por bicicleta
CREATE UNIQUE INDEX uq_trips_one_active_per_bike ON trips (bike_id) WHERE ended_at IS NULL;
CREATE INDEX idx_trips_rider ON trips (rider_id, started_at DESC);
CREATE INDEX idx_trips_bike ON trips (bike_id, started_at DESC);
CREATE INDEX idx_trips_ended ON trips (ended_at);
CREATE INDEX idx_trips_route ON trips USING GIST (route);

-- ---------------------------------------------------------------------------
-- Event store: cópia durável dos eventos de domínio que passam no Kafka
-- (fleet.events.status, fleet.alerts, ...). O Kafka guarda-os 7 dias; aqui ficam para auditoria,
-- BI e reprocessamento. Só se acrescentam linhas: alterar está bloqueado pelo trigger abaixo.
-- ---------------------------------------------------------------------------
CREATE TABLE fleet_events (
    event_id    UUID         PRIMARY KEY,               -- eventId/alertId: reenvios não duplicam
    topic       VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    bike_id     VARCHAR(16),
    occurred_at TIMESTAMPTZ  NOT NULL,
    payload     JSONB        NOT NULL,
    ingested_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_fleet_events_bike_time ON fleet_events (bike_id, occurred_at DESC);
CREATE INDEX idx_fleet_events_type_time ON fleet_events (event_type, occurred_at DESC);

CREATE FUNCTION fleet_events_forbid_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'fleet_events is append-only: events cannot be changed';
END;
$$;

CREATE TRIGGER trg_fleet_events_append_only
    BEFORE UPDATE ON fleet_events
    FOR EACH ROW EXECUTE FUNCTION fleet_events_forbid_update();

-- ---------------------------------------------------------------------------
-- Retenção (GDPR): viagens apagadas 90 dias depois de terminarem.
-- Chamada periodicamente pelo Fleet Management Service (ex.: @Scheduled diário).
-- ---------------------------------------------------------------------------
CREATE FUNCTION purge_expired_trips(retention INTERVAL DEFAULT INTERVAL '90 days')
RETURNS INTEGER
LANGUAGE plpgsql AS $$
DECLARE
    removed INTEGER;
BEGIN
    DELETE FROM trips WHERE ended_at < now() - retention;
    GET DIAGNOSTICS removed = ROW_COUNT;
    RETURN removed;
END;
$$;

-- ---------------------------------------------------------------------------
-- Vistas para a Core API (KPIs da frota, RF09) e para o mapa
-- ---------------------------------------------------------------------------
CREATE VIEW v_fleet_state_counts AS
SELECT state, count(*) AS bikes
FROM bikes
GROUP BY state;

CREATE VIEW v_bike_overview AS
SELECT b.bike_id,
       b.state,
       b.state_changed_at,
       p.battery_level,
       p.speed_kmh,
       ST_Y(p.location) AS latitude,
       ST_X(p.location) AS longitude,
       z.code           AS zone_code,
       z.name           AS zone_name,
       p.recorded_at    AS position_at,
       (SELECT count(*) FROM maintenance_tickets t
         WHERE t.bike_id = b.bike_id AND t.status <> 'RESOLVED') AS open_tickets
FROM bikes b
LEFT JOIN bike_positions p ON p.bike_id = b.bike_id
LEFT JOIN zones z ON z.zone_id = p.zone_id;

-- ---------------------------------------------------------------------------
-- Zonas de exemplo à volta do circuito do simulador (centro 40.6405, -8.6538; raio 0,6 km).
-- Substituir pelas zonas reais da frota piloto.
-- ---------------------------------------------------------------------------
INSERT INTO zones (code, name, kind, area) VALUES
    ('CAMPUS_AVEIRO', 'Campus Aveiro (área de operação)', 'OPERATING_AREA',
        ST_GeomFromText('POLYGON((-8.6643 40.6325, -8.6433 40.6325, -8.6433 40.6485, -8.6643 40.6485, -8.6643 40.6325))', 4326)),
    ('NORTH_CAMPUS', 'North Campus', 'HUB',
        ST_GeomFromText('POLYGON((-8.6643 40.6405, -8.6538 40.6405, -8.6538 40.6485, -8.6643 40.6485, -8.6643 40.6405))', 4326)),
    ('LIBRARY_HUB', 'Library Hub', 'HUB',
        ST_GeomFromText('POLYGON((-8.6538 40.6405, -8.6433 40.6405, -8.6433 40.6485, -8.6538 40.6485, -8.6538 40.6405))', 4326)),
    ('STATION_HUB', 'Station Hub', 'HUB',
        ST_GeomFromText('POLYGON((-8.6538 40.6325, -8.6433 40.6325, -8.6433 40.6405, -8.6538 40.6405, -8.6538 40.6325))', 4326)),
    ('SPORTS_HALL', 'Sports Hall', 'HUB',
        ST_GeomFromText('POLYGON((-8.6643 40.6325, -8.6538 40.6325, -8.6538 40.6405, -8.6643 40.6405, -8.6643 40.6325))', 4326));
