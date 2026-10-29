package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
public class AutomaticMeasurementRunStore {
    private final JdbcTemplate jdbc;
    public AutomaticMeasurementRunStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** Return only after this independent transaction commits, before requesting hardware work. */
    public boolean claim(String device, UUID cultivation, Instant slot, UUID key) {
        return jdbc.update("""
                insert into tds_automatic_measurement_run(device_id,cultivation_id,scheduled_at,idempotency_key)
                values (?,?,?,?) on conflict (device_id,scheduled_at) do nothing
                """, device, cultivation, Timestamp.from(slot), key) == 1;
    }
    public void finish(UUID key, String outcome, String reason) {
        jdbc.update("""
                update tds_automatic_measurement_run a set outcome=?,reason=?,updated_at=now(),
                command_id=(select c.command_id from tds_device_command c where c.idempotency_key=a.idempotency_key)
                where a.idempotency_key=? and a.outcome='CLAIMED'
                """, outcome, reason, key);
    }
    /** An interrupted claim is never sent again, even when it contains no command. */
    public void reconcileBefore(Instant slot) {
        jdbc.update("""
                update tds_automatic_measurement_run a set updated_at=now(),reason='PROCESS_INTERRUPTED',
                outcome=case when exists(select 1 from tds_device_command c where c.idempotency_key=a.idempotency_key)
                    then 'SUBMITTED' else 'UNKNOWN' end,
                command_id=(select c.command_id from tds_device_command c where c.idempotency_key=a.idempotency_key)
                where a.outcome='CLAIMED' and a.scheduled_at < ?
                """, Timestamp.from(slot));
    }
}
