CREATE TABLE water_maintenance (
    maintenance_id UUID PRIMARY KEY,
    cultivation_id UUID NOT NULL REFERENCES cultivation(cultivation_id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('REPLACE','TOP_UP','NUTRIENT_ADD')),
    replacement_scope VARCHAR(8),
    occurred_at TIMESTAMPTZ NOT NULL,
    amount_ml NUMERIC(12,2) CHECK (amount_ml > 0),
    memo VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((type='REPLACE' AND replacement_scope IS NOT NULL AND replacement_scope IN ('FULL','PARTIAL'))
        OR (type<>'REPLACE' AND replacement_scope IS NULL))
);
CREATE INDEX ix_maintenance_timeline ON water_maintenance(cultivation_id,occurred_at,maintenance_id);
