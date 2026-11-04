-- Keep the first registration input independent of editable current values.
ALTER TABLE cultivation ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE cultivation ADD COLUMN initial_plant_name VARCHAR(100);
ALTER TABLE cultivation ADD COLUMN initial_name VARCHAR(100);
ALTER TABLE cultivation ADD COLUMN initial_memo VARCHAR(2000);
UPDATE cultivation SET initial_plant_name=plant_name, initial_name=name, initial_memo=memo;
ALTER TABLE cultivation ALTER COLUMN initial_plant_name SET NOT NULL;
ALTER TABLE cultivation ALTER COLUMN initial_name SET NOT NULL;
ALTER TABLE cultivation ALTER COLUMN initial_memo SET NOT NULL;

ALTER TABLE water_maintenance ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE water_maintenance ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE water_maintenance ADD COLUMN initial_type VARCHAR(16);
ALTER TABLE water_maintenance ADD COLUMN initial_scope VARCHAR(8);
ALTER TABLE water_maintenance ADD COLUMN initial_occurred_at TIMESTAMPTZ;
ALTER TABLE water_maintenance ADD COLUMN initial_amount_ml NUMERIC(12,2);
ALTER TABLE water_maintenance ADD COLUMN initial_memo VARCHAR(2000);
UPDATE water_maintenance SET updated_at=created_at, initial_type=type, initial_scope=replacement_scope,
    initial_occurred_at=occurred_at, initial_amount_ml=amount_ml, initial_memo=memo;
ALTER TABLE water_maintenance ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE water_maintenance ALTER COLUMN initial_type SET NOT NULL;
ALTER TABLE water_maintenance ALTER COLUMN initial_occurred_at SET NOT NULL;
ALTER TABLE water_maintenance ALTER COLUMN initial_memo SET NOT NULL;

-- Single-owner edit key space. A successful receipt also holds the before/after audit.
-- No-op requests have a receipt but no before/after change pair.
CREATE TABLE care_edit_receipt (
    request_key UUID PRIMARY KEY,
    request_path VARCHAR(200) NOT NULL,
    request_body TEXT NOT NULL,
    response_body TEXT NOT NULL,
    before_body TEXT,
    after_body TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((before_body IS NULL) = (after_body IS NULL))
);
