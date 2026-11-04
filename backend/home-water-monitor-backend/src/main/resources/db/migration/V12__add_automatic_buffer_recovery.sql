-- A recovery is distinct from the new START belonging to the hourly run.
CREATE TABLE tds_automatic_recovery (
    device_id VARCHAR(64) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    session_id UUID NOT NULL,
    boot_id UUID NOT NULL,
    retry_key UUID NOT NULL UNIQUE,
    command_id UUID REFERENCES tds_device_command(command_id),
    outcome VARCHAR(24) NOT NULL DEFAULT 'PREPARED',
    reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(device_id, scheduled_at),
    FOREIGN KEY(device_id, scheduled_at) REFERENCES tds_automatic_measurement_run(device_id, scheduled_at),
    CHECK(outcome IN ('PREPARED','SUBMITTED','RECOVERED','STOPPED','UNKNOWN'))
);
