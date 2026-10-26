ALTER TABLE tds_device_command ADD COLUMN cultivation_id UUID REFERENCES cultivation(cultivation_id);
ALTER TABLE tds_device_command ADD CONSTRAINT ck_command_cultivation_start
    CHECK (cultivation_id IS NULL OR command_type='START');
CREATE UNIQUE INDEX uk_command_start_session ON tds_device_command(session_id) WHERE command_type='START';
CREATE INDEX ix_command_cultivation ON tds_device_command(cultivation_id,requested_at) WHERE cultivation_id IS NOT NULL;
