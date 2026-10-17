CREATE TABLE tds_session (
                             session_id UUID PRIMARY KEY,
                             device_id VARCHAR(64) NOT NULL,
                             started_at TIMESTAMPTZ,
                             ingested_at TIMESTAMPTZ NOT NULL,

                             expected_sample_count INTEGER NOT NULL,
                             sampling_interval_ms INTEGER NOT NULL,
                             preserved_sample_count INTEGER NOT NULL,
                             stats_sample_count INTEGER NOT NULL,
                             preservation_result VARCHAR(16) NOT NULL,

                             temperature_assumed_c NUMERIC(5, 2) NOT NULL,
                             calibration_version VARCHAR(64) NOT NULL,

                             min_tds_ppm NUMERIC(9, 2),
                             max_tds_ppm NUMERIC(9, 2),
                             avg_tds_ppm NUMERIC(9, 2),
                             median_tds_ppm NUMERIC(9, 2),

                             CONSTRAINT ck_tds_session_expected_count
                                 CHECK (expected_sample_count > 0),

                             CONSTRAINT ck_tds_session_interval
                                 CHECK (sampling_interval_ms > 0),

                             CONSTRAINT ck_tds_session_sample_counts
                                 CHECK (
                                     preserved_sample_count BETWEEN 0 AND expected_sample_count
                                         AND stats_sample_count BETWEEN 0 AND preserved_sample_count
                                     ),

                             CONSTRAINT ck_tds_session_result
                                 CHECK (preservation_result IN ('NORMAL', 'INCOMPLETE'))
);