package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Durable intent precedes submission; this table never authorizes an unrecorded second RETRY. */
@Repository
@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=5)
public class AutomaticRecoveryStore {
    private final JdbcTemplate jdbc;
    public AutomaticRecoveryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean prepare(String device,Instant slot,UUID session,UUID boot,UUID key) {
        return jdbc.update("""
            insert into tds_automatic_recovery(device_id,scheduled_at,session_id,boot_id,retry_key)
            values(?,?,?,?,?) on conflict(device_id,scheduled_at) do nothing
            """,device,Timestamp.from(slot),session,boot,key)==1;
    }
    public void finish(UUID key,String outcome,String reason) {
        jdbc.update("""
            update tds_automatic_recovery r set outcome=?,reason=?,updated_at=now(),
            command_id=(select c.command_id from tds_device_command c where c.idempotency_key=r.retry_key)
            where retry_key=? and outcome in ('PREPARED','SUBMITTED')
            """,outcome,reason,key);
    }
    public void reconcileBefore(Instant slot) {
        // A restarted process observes the ledger rather than replaying an uncertain POST.
        jdbc.update("""
            update tds_automatic_recovery r set outcome='UNKNOWN',reason='PROCESS_INTERRUPTED',updated_at=now(),
            command_id=(select c.command_id from tds_device_command c where c.idempotency_key=r.retry_key)
            where outcome in ('PREPARED','SUBMITTED') and scheduled_at < ?
            """,Timestamp.from(slot));
    }
}
