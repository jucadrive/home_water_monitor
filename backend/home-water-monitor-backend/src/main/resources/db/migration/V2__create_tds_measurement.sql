CREATE TABLE tds_measurement (
                                 measurement_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 session_id UUID NOT NULL,
                                 seq INTEGER NOT NULL,
                                 elapsed_ms INTEGER NOT NULL,
                                 tds_ppm NUMERIC(9, 2) NOT NULL,
                                 is_warmup BOOLEAN NOT NULL,
                                 raw_voltage_mv NUMERIC(9, 2),

                                 CONSTRAINT fk_tds_measurement_session
                                     FOREIGN KEY (session_id)
                                         REFERENCES tds_session (session_id),

                                 CONSTRAINT uk_tds_measurement_session_seq
                                     UNIQUE (session_id, seq),

                                 CONSTRAINT ck_tds_measurement_seq
                                     CHECK (seq > 0),

                                 CONSTRAINT ck_tds_measurement_elapsed
                                     CHECK (elapsed_ms >= 0)
);