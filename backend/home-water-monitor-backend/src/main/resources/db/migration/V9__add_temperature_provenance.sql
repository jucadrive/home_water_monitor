ALTER TABLE tds_session ALTER COLUMN temperature_assumed_c DROP NOT NULL;
ALTER TABLE tds_session ADD COLUMN temperature_source VARCHAR(16), ADD COLUMN compensation_version VARCHAR(64);
ALTER TABLE tds_session ADD CONSTRAINT ck_temperature_context CHECK (
 (temperature_source IS NULL AND compensation_version IS NULL AND temperature_assumed_c IS NOT NULL) OR
 (temperature_source IS NOT NULL AND temperature_source IN ('DS18B20','SIMULATED') AND compensation_version IS NOT NULL
  AND compensation_version = 'sen0244-voltage-atc-v1' AND temperature_assumed_c IS NULL));
ALTER TABLE tds_measurement ADD COLUMN temperature_c NUMERIC(7,4), ADD COLUMN temperature_elapsed_ms INTEGER;
ALTER TABLE tds_measurement ADD CONSTRAINT ck_temperature_sample CHECK (
 (temperature_c IS NULL AND temperature_elapsed_ms IS NULL) OR
 (temperature_c IS NOT NULL AND temperature_elapsed_ms IS NOT NULL AND temperature_c BETWEEN 0 AND 50
  AND temperature_elapsed_ms BETWEEN 0 AND elapsed_ms AND elapsed_ms::bigint-temperature_elapsed_ms <= 2000));
ALTER TABLE tds_measurement_redelivery ADD COLUMN original_temperature_c NUMERIC(7,4),
 ADD COLUMN original_temperature_elapsed_ms INTEGER, ADD COLUMN incoming_temperature_c NUMERIC(7,4),
 ADD COLUMN incoming_temperature_elapsed_ms INTEGER;
