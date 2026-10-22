package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.application.control.ControlModel.Status;
import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.ControlMessageCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 제어 전용 SQL·JSONB·짧은 잠금을 격리한다. application 트랜잭션 안에서 쓰기를 수행한다.
@Repository
public class DeviceControlStore {
    private final JdbcTemplate jdbc;
    private final ControlMessageCodec codec = new ControlMessageCodec();
    public DeviceControlStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Slot(String deviceId, UUID boot, UUID active, UUID held) { }
    public record Observation(Status status, Instant receivedAt, Instant confirmedAt, UUID epoch) { }
    public Slot lock(String device) {
        jdbc.queryForObject("select set_config('lock_timeout','1000ms',true)", String.class);
        jdbc.queryForObject("select set_config('statement_timeout','2000ms',true)", String.class);
        jdbc.update("insert into tds_device_control(device_id,updated_at) values (?,now()) on conflict do nothing", device);
        return jdbc.queryForObject("select * from tds_device_control where device_id=? for update", (r,n) ->
                new Slot(device, r.getObject("current_boot_id",UUID.class), r.getObject("active_command_id",UUID.class),
                        r.getObject("held_session_id",UUID.class)), device);
    }
    public void slot(String device, UUID boot, UUID active, UUID held) {
        jdbc.update("update tds_device_control set current_boot_id=?,active_command_id=?,held_session_id=?,version=version+1,updated_at=now() where device_id=?",
                boot,active,held,device);
    }
    public ControlCommand byKey(UUID key) { return one("idempotency_key",key); }
    public ControlCommand command(UUID id) { return id == null ? null : one("command_id",id); }
    private ControlCommand one(String column, UUID id) {
        var rows = jdbc.query("select * from tds_device_command where " + column + "=?", this::commandRow, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private ControlCommand commandRow(ResultSet r,int n) throws SQLException {
        var dispatch = r.getTimestamp("dispatching_at");
        return new ControlCommand(r.getObject("command_id",UUID.class),r.getObject("idempotency_key",UUID.class),
                r.getString("device_id"),r.getString("command_type"),r.getObject("session_id",UUID.class),
                ControlModel.State.valueOf(r.getString("command_state")),r.getObject("target_boot_id",UUID.class),
                r.getObject("window_id",UUID.class),r.getString("dispatch_payload"),r.getTimestamp("requested_at").toInstant(),
                dispatch == null ? null : dispatch.toInstant(),r.getString("reason"),
                r.getTimestamp("accepted_at")==null?null:r.getTimestamp("accepted_at").toInstant(),
                r.getTimestamp("release_confirmed_at")==null?null:r.getTimestamp("release_confirmed_at").toInstant());
    }
    public ControlCommand create(UUID key,String device,String type,UUID session) {
        UUID id=UUID.randomUUID();
        String request = "START".equals(type) ? "{\"type\":\"START\"}"
                : "{\"type\":\"RETRY\",\"sessionId\":\""+session+"\"}";
        jdbc.update("""
                insert into tds_device_command(command_id,idempotency_key,device_id,command_type,normalized_request,
                session_id,command_state,requested_at,updated_at) values (?,?,?,?,cast(? as jsonb),?,'REQUESTED',now(),now())
                """,id,key,device,type,request,session);
        return command(id);
    }
    public void dispatch(UUID id,UUID boot,UUID window,String payload) {
        jdbc.update("""
                update tds_device_command set command_state='DISPATCHING',target_boot_id=?,window_id=?,dispatch_payload=?,
                dispatching_at=now(),updated_at=now() where command_id=? and command_state='PROBING'
                """,boot,window,payload,id);
    }
    public void state(UUID id,ControlModel.State state,String reason) {
        jdbc.update("""
                update tds_device_command set command_state=?,reason=?,decided_at=now(),updated_at=now(),
                accepted_at=case when ?='ACCEPTED' then coalesce(accepted_at,now()) else accepted_at end
                where command_id=? and (command_state not in ('ACCEPTED','REJECTED','NOT_DISPATCHED','NOT_EXECUTED','INTERRUPTED_REBOOT')
                or command_state='ACCEPTED' and ?='INTERRUPTED_REBOOT' and release_confirmed_at is null)
                """,state.name(),reason,state.name(),id,state.name());
    }
    public Observation observation(String device) {
        var rows=jdbc.query("select * from tds_device_observation where device_id=?", (r,n) -> {
            var t=r.getTimestamp("confirmed_at");
            return new Observation(codec.storedStatus(r.getString("snapshot")), r.getTimestamp("received_at").toInstant(),
                    t == null ? null : t.toInstant(), r.getObject("server_epoch",UUID.class));
        },device);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean retired(String device,UUID boot) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tds_device_boot where device_id=? and boot_id=? and retired_at is not null)",Boolean.class,device,boot));
    }
    public void boot(String device,UUID boot) {
        jdbc.update("update tds_device_boot set retired_at=now() where device_id=? and boot_id<>? and retired_at is null",device,boot);
        jdbc.update("insert into tds_device_boot(device_id,boot_id,confirmed_at) values (?,?,now()) on conflict do nothing",device,boot);
    }
    public void observation(Status s,boolean probe,UUID epoch,Instant received) {
        jdbc.update("""
                insert into tds_device_observation(device_id,boot_id,status_seq,snapshot,received_at,last_probe_id,confirmed_at,server_epoch)
                values (?,?,?,cast(? as jsonb),?,?,case when ? then now() else null end,?)
                on conflict (device_id) do update set boot_id=excluded.boot_id,status_seq=excluded.status_seq,
                snapshot=excluded.snapshot,received_at=excluded.received_at,
                last_probe_id=case when ? then excluded.last_probe_id else tds_device_observation.last_probe_id end,
                confirmed_at=case when ? then excluded.confirmed_at else tds_device_observation.confirmed_at end,
                server_epoch=case when ? then excluded.server_epoch else tds_device_observation.server_epoch end
                """,s.deviceId(),s.bootId(),s.statusSeq(),codec.json(s),java.sql.Timestamp.from(received),s.replyToProbeId(),probe,epoch,probe,probe,probe);
    }
    public String preservation(UUID session) {
        var r=jdbc.queryForList("select preservation_result from tds_session where session_id=?",String.class,session);
        return r.isEmpty()?"NOT_OBSERVED":r.getFirst();
    }
    public boolean preserved(UUID session,String result,int count) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tds_session where session_id=? and preservation_result=? and preserved_sample_count=?)",Boolean.class,session,result,count));
    }
    public void released(String device,UUID session,UUID boot) {
        jdbc.update("update tds_device_command set release_confirmed_at=coalesce(release_confirmed_at,now()) where device_id=? and session_id=? and target_boot_id=?",device,session,boot);
    }
    public List<ControlCommand> unfinished() {
        return jdbc.query("select * from tds_device_command where command_state in ('REQUESTED','PROBING','DISPATCHING','UNKNOWN')",this::commandRow);
    }
    public boolean keyConflict(Throwable error) {
        for(Throwable t=error;t!=null;t=t.getCause()) if(t instanceof SQLException p && "23505".equals(p.getSQLState())) {
            try {
                var server=t.getClass().getMethod("getServerErrorMessage").invoke(t);
                return server!=null && "tds_device_command_idempotency_key_key".equals(server.getClass().getMethod("getConstraint").invoke(server));
            } catch (ReflectiveOperationException ignored) { return false; }
        }
        return false;
    }
}
