-- Sentinel Grid 3.5 / Omniscience: adopt the legacy Hibernate-managed table and
-- install a deterministic, non-destructive 21-unit fleet catalog.
CREATE TABLE IF NOT EXISTS drones (
    id UUID PRIMARY KEY,
    call_sign VARCHAR(255) NOT NULL UNIQUE,
    model VARCHAR(255),
    status VARCHAR(255),
    battery_capacity_mah INTEGER,
    created_at TIMESTAMP(6),
    model_class VARCHAR(32),
    top_speed_kmh DOUBLE PRECISION,
    radar_range_m DOUBLE PRECISION,
    payload_capacity_kg DOUBLE PRECISION,
    vision_modes TEXT[]
);

ALTER TABLE drones ADD COLUMN IF NOT EXISTS model_class VARCHAR(32);
ALTER TABLE drones ADD COLUMN IF NOT EXISTS top_speed_kmh DOUBLE PRECISION;
ALTER TABLE drones ADD COLUMN IF NOT EXISTS radar_range_m DOUBLE PRECISION;
ALTER TABLE drones ADD COLUMN IF NOT EXISTS payload_capacity_kg DOUBLE PRECISION;
ALTER TABLE drones ADD COLUMN IF NOT EXISTS vision_modes TEXT[];

-- Existing operator-created rows are retained and receive a conservative dossier.
UPDATE drones SET model = 'LEGACY-UNCLASSIFIED' WHERE model IS NULL OR BTRIM(model) = '';
UPDATE drones SET status = 'OFFLINE' WHERE status IS NULL OR BTRIM(status) = '';
UPDATE drones SET battery_capacity_mah = 10000 WHERE battery_capacity_mah IS NULL OR battery_capacity_mah <= 0;
UPDATE drones SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE drones SET model_class = 'RECON' WHERE model_class IS NULL;
UPDATE drones SET top_speed_kmh = 120 WHERE top_speed_kmh IS NULL OR top_speed_kmh <= 0;
UPDATE drones SET radar_range_m = 5000 WHERE radar_range_m IS NULL OR radar_range_m <= 0;
UPDATE drones SET payload_capacity_kg = 1 WHERE payload_capacity_kg IS NULL OR payload_capacity_kg <= 0;
UPDATE drones SET vision_modes = ARRAY['Standard']::TEXT[]
WHERE vision_modes IS NULL OR CARDINALITY(vision_modes) = 0;

ALTER TABLE drones ALTER COLUMN model SET NOT NULL;
ALTER TABLE drones ALTER COLUMN status SET NOT NULL;
ALTER TABLE drones ALTER COLUMN battery_capacity_mah SET NOT NULL;
ALTER TABLE drones ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE drones ALTER COLUMN model_class SET NOT NULL;
ALTER TABLE drones ALTER COLUMN top_speed_kmh SET NOT NULL;
ALTER TABLE drones ALTER COLUMN radar_range_m SET NOT NULL;
ALTER TABLE drones ALTER COLUMN payload_capacity_kg SET NOT NULL;
ALTER TABLE drones ALTER COLUMN vision_modes SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_model_class') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_model_class
            CHECK (model_class IN ('RECON', 'INTERCEPTOR', 'HEAVY_SUPPORT'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_battery_positive') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_battery_positive CHECK (battery_capacity_mah > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_top_speed_positive') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_top_speed_positive CHECK (top_speed_kmh > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_radar_range_positive') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_radar_range_positive CHECK (radar_range_m > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_payload_positive') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_payload_positive CHECK (payload_capacity_kg > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_vision_modes_present') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_vision_modes_present
            CHECK (CARDINALITY(vision_modes) > 0 AND ARRAY_POSITION(vision_modes, '') IS NULL);
    END IF;
END $$;

INSERT INTO drones (
    id, call_sign, model, status, battery_capacity_mah, created_at,
    model_class, top_speed_kmh, radar_range_m, payload_capacity_kg, vision_modes
) VALUES
    ('00000000-0000-3500-8000-000000000001', 'SPECTRE-01', 'SPECTRE-IV', 'OFFLINE', 14000, CURRENT_TIMESTAMP, 'RECON', 245, 32000, 2, ARRAY['Standard','Thermal','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000002', 'ZEPHYR-02', 'ZEPHYR-1', 'OFFLINE', 10000, CURRENT_TIMESTAMP, 'RECON', 280, 26000, 1.5, ARRAY['Standard','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000003', 'ORACLE-03', 'ORACLE-K2', 'OFFLINE', 18000, CURRENT_TIMESTAMP, 'RECON', 215, 42000, 3.5, ARRAY['Standard','Thermal','Hyperspectral']::TEXT[]),
    ('00000000-0000-3500-8000-000000000004', 'NIGHTJAR-04', 'NIGHTJAR-6', 'OFFLINE', 15000, CURRENT_TIMESTAMP, 'RECON', 235, 30000, 2.5, ARRAY['Standard','Thermal','Infrared']::TEXT[]),
    ('00000000-0000-3500-8000-000000000005', 'ARGUS-05', 'ARGUS-V', 'OFFLINE', 20000, CURRENT_TIMESTAMP, 'RECON', 200, 46000, 4, ARRAY['Standard','Thermal','Multispectral','Hyperspectral']::TEXT[]),
    ('00000000-0000-3500-8000-000000000006', 'PHANTOM-06', 'PHANTOM-S9', 'OFFLINE', 13000, CURRENT_TIMESTAMP, 'RECON', 255, 28500, 2, ARRAY['Standard','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000007', 'GHOST-07', 'GHOSTLANCE-R3', 'OFFLINE', 11000, CURRENT_TIMESTAMP, 'RECON', 270, 24000, 1, ARRAY['Standard','Thermal','Infrared']::TEXT[]),
    ('00000000-0000-3500-8000-000000000011', 'CHIMERA-11', 'CHIMERA-7', 'OFFLINE', 18000, CURRENT_TIMESTAMP, 'INTERCEPTOR', 365, 18000, 9, ARRAY['Standard','Thermal','Infrared']::TEXT[]),
    ('00000000-0000-3500-8000-000000000012', 'RAZOR-12', 'RAZORWING-M5', 'OFFLINE', 16500, CURRENT_TIMESTAMP, 'INTERCEPTOR', 420, 15000, 7, ARRAY['Standard','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000013', 'TEMPEST-13', 'TEMPEST-I4', 'OFFLINE', 20000, CURRENT_TIMESTAMP, 'INTERCEPTOR', 395, 20000, 11, ARRAY['Standard','Thermal','Infrared']::TEXT[]),
    ('00000000-0000-3500-8000-000000000014', 'VIPER-14', 'VIPER-A8', 'OFFLINE', 15500, CURRENT_TIMESTAMP, 'INTERCEPTOR', 440, 14000, 6, ARRAY['Standard','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000015', 'LANCER-15', 'LANCER-Q6', 'OFFLINE', 22000, CURRENT_TIMESTAMP, 'INTERCEPTOR', 380, 22000, 12, ARRAY['Standard','Thermal','Multispectral']::TEXT[]),
    ('00000000-0000-3500-8000-000000000016', 'FALCON-16', 'FALCON-XR', 'OFFLINE', 19000, CURRENT_TIMESTAMP, 'INTERCEPTOR', 410, 19000, 8, ARRAY['Standard','Thermal','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000017', 'MANTIS-17', 'MANTIS-J9', 'OFFLINE', 24000, CURRENT_TIMESTAMP, 'INTERCEPTOR', 350, 24000, 14, ARRAY['Standard','Thermal','Infrared','Multispectral']::TEXT[]),
    ('00000000-0000-3500-8000-000000000021', 'TITAN-21', 'TITAN-X9', 'OFFLINE', 78000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 145, 18000, 110, ARRAY['Standard','Thermal','Infrared']::TEXT[]),
    ('00000000-0000-3500-8000-000000000022', 'VULCAN-22', 'VULCAN-H2', 'OFFLINE', 68000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 165, 20000, 90, ARRAY['Standard','Thermal','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000023', 'COLOSSUS-23', 'COLOSSUS-M8', 'OFFLINE', 90000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 125, 24000, 130, ARRAY['Standard','Thermal','Multispectral']::TEXT[]),
    ('00000000-0000-3500-8000-000000000024', 'BASTION-24', 'BASTION-C4', 'OFFLINE', 85000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 135, 26000, 120, ARRAY['Standard','Thermal','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000025', 'ATLAS-25', 'ATLAS-G7', 'OFFLINE', 75000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 155, 22000, 100, ARRAY['Standard','Thermal','Hyperspectral']::TEXT[]),
    ('00000000-0000-3500-8000-000000000026', 'WARHAMMER-26', 'WARHAMMER-D3', 'OFFLINE', 62000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 180, 16000, 75, ARRAY['Standard','Infrared','Low-Light']::TEXT[]),
    ('00000000-0000-3500-8000-000000000027', 'GOLIATH-27', 'GOLIATH-K5', 'OFFLINE', 88000, CURRENT_TIMESTAMP, 'HEAVY_SUPPORT', 140, 28000, 125, ARRAY['Standard','Thermal','Infrared','Multispectral']::TEXT[])
ON CONFLICT (call_sign) DO UPDATE SET
    model = EXCLUDED.model,
    battery_capacity_mah = EXCLUDED.battery_capacity_mah,
    model_class = EXCLUDED.model_class,
    top_speed_kmh = EXCLUDED.top_speed_kmh,
    radar_range_m = EXCLUDED.radar_range_m,
    payload_capacity_kg = EXCLUDED.payload_capacity_kg,
    vision_modes = EXCLUDED.vision_modes;
