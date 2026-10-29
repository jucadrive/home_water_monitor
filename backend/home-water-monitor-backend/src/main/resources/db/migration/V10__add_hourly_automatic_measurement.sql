CREATE TABLE tds_automatic_measurement_run (
    device_id VARCHAR(64) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    cultivation_id UUID NOT NULL REFERENCES cultivation(cultivation_id),
    idempotency_key UUID NOT NULL UNIQUE,
    command_id UUID REFERENCES tds_device_command(command_id),
    outcome VARCHAR(16) NOT NULL DEFAULT 'CLAIMED',
    reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, scheduled_at),
    CONSTRAINT ck_hourly_automatic_slot CHECK (scheduled_at = date_trunc('hour', scheduled_at, 'UTC')),
    CONSTRAINT ck_hourly_automatic_outcome CHECK (outcome IN ('CLAIMED','SUBMITTED','SKIPPED','UNKNOWN'))
);
