-- 제어 요청은 실제 측정 원본과 별개다. 요청만으로 tds_session을 생성하지 않는다.
CREATE TABLE tds_device_control (
    device_id VARCHAR(64) PRIMARY KEY,
    current_boot_id UUID,
    active_command_id UUID,
    held_session_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE tds_device_command (
    command_id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    device_id VARCHAR(64) NOT NULL REFERENCES tds_device_control(device_id),
    command_type VARCHAR(8) NOT NULL CHECK (command_type IN ('START','RETRY')),
    normalized_request JSONB NOT NULL,
    session_id UUID NOT NULL,
    command_state VARCHAR(24) NOT NULL CHECK (command_state IN ('REQUESTED','PROBING','DISPATCHING','ACCEPTED',
        'REJECTED','NOT_DISPATCHED','NOT_EXECUTED','UNKNOWN','INTERRUPTED_REBOOT')),
    target_boot_id UUID,
    window_id UUID,
    dispatch_payload TEXT CHECK (octet_length(dispatch_payload) <= 4096),
    requested_at TIMESTAMPTZ NOT NULL,
    dispatching_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    release_confirmed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(64),
    CHECK (command_state NOT IN ('DISPATCHING','ACCEPTED','UNKNOWN') OR
        (target_boot_id IS NOT NULL AND window_id IS NOT NULL AND dispatch_payload IS NOT NULL AND dispatching_at IS NOT NULL))
);
ALTER TABLE tds_device_control ADD CONSTRAINT fk_active_device_command
    FOREIGN KEY (active_command_id) REFERENCES tds_device_command(command_id);
CREATE TABLE tds_device_observation (
    device_id VARCHAR(64) PRIMARY KEY REFERENCES tds_device_control(device_id),
    boot_id UUID NOT NULL,
    status_seq BIGINT NOT NULL CHECK (status_seq BETWEEN 1 AND 2147483647),
    snapshot JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    last_probe_id UUID,
    confirmed_at TIMESTAMPTZ,
    server_epoch UUID
);
CREATE TABLE tds_device_boot (
    device_id VARCHAR(64) NOT NULL REFERENCES tds_device_control(device_id),
    boot_id UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    retired_at TIMESTAMPTZ,
    PRIMARY KEY (device_id,boot_id)
);
CREATE INDEX ix_device_command_requested ON tds_device_command(device_id,requested_at);
CREATE INDEX ix_device_command_recovery ON tds_device_command(command_state,updated_at);
CREATE INDEX ix_device_boot_retired ON tds_device_boot(device_id,retired_at);
