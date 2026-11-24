-- Correções ao modelo de dados depois da revisão do PR #6 (KIQ-45).
-- Vai numa migração nova porque a V2 já foi aplicada: alterar a V2 faria o Flyway falhar.
-- "2_1" = versão 2.1: corre depois da V2 e antes da V3 (registo das bicicletas, KIQ-58).

-- ---------------------------------------------------------------------------
-- 1. fleet_events: append-only também para DELETE.
--    Só as funções de retenção e de direito ao esquecimento (abaixo) podem apagar:
--    ligam a flag kinetiq.allow_event_delete apenas dentro da sua própria transação.
-- ---------------------------------------------------------------------------
DROP TRIGGER trg_fleet_events_append_only ON fleet_events;
DROP FUNCTION fleet_events_forbid_update();

CREATE FUNCTION fleet_events_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'fleet_events is append-only: events cannot be changed';
    END IF;
    IF coalesce(current_setting('kinetiq.allow_event_delete', true), '') <> 'on' THEN
        RAISE EXCEPTION 'fleet_events is append-only: use purge_expired_fleet_events() or forget_rider()';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_fleet_events_append_only
    BEFORE UPDATE OR DELETE ON fleet_events
    FOR EACH ROW EXECUTE FUNCTION fleet_events_guard();

-- ---------------------------------------------------------------------------
-- 2. Retenção da fleet_events (GDPR): os eventos de estado trazem o actorId (id Keycloak do rider).
--    400 dias, como os agregados do InfluxDB (BI). Chamada uma vez por dia pelo Fleet Management.
-- ---------------------------------------------------------------------------
CREATE FUNCTION purge_expired_fleet_events(retention INTERVAL DEFAULT INTERVAL '400 days')
RETURNS INTEGER
LANGUAGE plpgsql AS $$
DECLARE
    removed INTEGER;
BEGIN
    PERFORM set_config('kinetiq.allow_event_delete', 'on', true);
    DELETE FROM fleet_events WHERE occurred_at < now() - retention;
    GET DIAGNOSTICS removed = ROW_COUNT;
    PERFORM set_config('kinetiq.allow_event_delete', 'off', true);
    RETURN removed;
END;
$$;

-- ---------------------------------------------------------------------------
-- 3. Direito ao esquecimento (slide "Privacy by design"): quando um rider apaga a conta.
--    Apaga as viagens e os eventos com o id dele e tira o id do histórico de estados
--    (a mudança de estado da bicicleta continua registada, mas sem dizer quem a pediu).
-- ---------------------------------------------------------------------------
CREATE FUNCTION forget_rider(p_rider_id VARCHAR)
RETURNS TABLE (trips_removed INTEGER, events_removed INTEGER, transitions_anonymised INTEGER)
LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM trips WHERE rider_id = p_rider_id;
    GET DIAGNOSTICS trips_removed = ROW_COUNT;

    PERFORM set_config('kinetiq.allow_event_delete', 'on', true);
    DELETE FROM fleet_events
     WHERE payload ->> 'actorId' = p_rider_id
        OR payload ->> 'riderId' = p_rider_id;
    GET DIAGNOSTICS events_removed = ROW_COUNT;
    PERFORM set_config('kinetiq.allow_event_delete', 'off', true);

    UPDATE bike_state_transitions SET actor_id = NULL WHERE actor_id = p_rider_id;
    GET DIAGNOSTICS transitions_anonymised = ROW_COUNT;

    RETURN NEXT;
END;
$$;

-- ---------------------------------------------------------------------------
-- 4. Em que zona está um ponto. As zonas sobrepõem-se (a área de operação contém os hubs),
--    por isso escolhe a mais específica: zona proibida > hub > área de operação;
--    em empate, a mais pequena. NULL se o ponto estiver fora de todas.
--    Usada ao gravar bike_positions.zone_id.
-- ---------------------------------------------------------------------------
CREATE FUNCTION zone_for_point(p geometry)
RETURNS INTEGER
LANGUAGE sql STABLE AS $$
    SELECT zone_id
      FROM zones
     WHERE ST_Covers(area, p)
     ORDER BY CASE kind WHEN 'RESTRICTED' THEN 0 WHEN 'HUB' THEN 1 ELSE 2 END,
              ST_Area(area)
     LIMIT 1;
$$;

-- ---------------------------------------------------------------------------
-- 5. Zona proibida de exemplo, para testar alertas UNAUTHORIZED_ZONE.
--    Fica dentro do Library Hub e o circuito do simulador atravessa-a (perto de 40.6443, -8.6488).
-- ---------------------------------------------------------------------------
INSERT INTO zones (code, name, kind, area) VALUES
    ('CONSTRUCTION_SITE', 'Construction site', 'RESTRICTED',
        ST_GeomFromText('POLYGON((-8.6500 40.6430, -8.6475 40.6430, -8.6475 40.6455, -8.6500 40.6455, -8.6500 40.6430))', 4326));
