-- 커밋한 재수신 목록과 당시 샘플별 비교 결과를 원본과 분리해 보존한다.
CREATE TABLE tds_session_redelivery (
    redelivery_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES tds_session(session_id),
    received_at TIMESTAMPTZ NOT NULL,
    sample_count INTEGER NOT NULL,
    identical_count INTEGER NOT NULL,
    conflict_count INTEGER NOT NULL,
    CONSTRAINT ck_redelivery_counts CHECK (
        sample_count = 60 AND identical_count >= 0 AND conflict_count >= 0
        AND identical_count + conflict_count = sample_count
    )
);

CREATE INDEX ix_redelivery_session_received ON tds_session_redelivery(session_id, received_at);

CREATE TABLE tds_measurement_redelivery (
    redelivery_id UUID NOT NULL REFERENCES tds_session_redelivery(redelivery_id),
    seq INTEGER NOT NULL CHECK (seq BETWEEN 1 AND 60),
    comparison_result VARCHAR(16) NOT NULL CHECK (comparison_result IN ('IDENTICAL', 'CONFLICT')),
    original_elapsed_ms INTEGER NOT NULL CHECK (original_elapsed_ms >= 0),
    incoming_elapsed_ms INTEGER NOT NULL CHECK (incoming_elapsed_ms >= 0),
    original_tds_ppm NUMERIC(9, 2) NOT NULL,
    incoming_tds_ppm NUMERIC(9, 2) NOT NULL,
    original_is_warmup BOOLEAN NOT NULL,
    incoming_is_warmup BOOLEAN NOT NULL,
    original_raw_voltage_mv NUMERIC(9, 2),
    incoming_raw_voltage_mv NUMERIC(9, 2),
    PRIMARY KEY (redelivery_id, seq)
);
