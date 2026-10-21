-- 최초 확정 목록에서 빠진 순번이다. 측정값을 생성하거나 보간하지 않는다.
CREATE TABLE tds_session_missing_sample (
    session_id UUID NOT NULL REFERENCES tds_session(session_id),
    seq INTEGER NOT NULL CHECK (seq BETWEEN 1 AND 60),
    PRIMARY KEY (session_id, seq)
);

ALTER TABLE tds_session_redelivery DROP CONSTRAINT ck_redelivery_counts;
ALTER TABLE tds_session_redelivery ADD CONSTRAINT ck_redelivery_counts CHECK (
    sample_count BETWEEN 0 AND 60 AND identical_count >= 0 AND conflict_count >= 0
    AND identical_count + conflict_count = sample_count
);
