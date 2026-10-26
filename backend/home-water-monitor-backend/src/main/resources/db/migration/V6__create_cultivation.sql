CREATE TABLE cultivation (
    cultivation_id UUID PRIMARY KEY,
    plant_name VARCHAR(100) NOT NULL CHECK (length(trim(plant_name)) > 0),
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    memo VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX ix_cultivation_created ON cultivation(created_at, cultivation_id);
