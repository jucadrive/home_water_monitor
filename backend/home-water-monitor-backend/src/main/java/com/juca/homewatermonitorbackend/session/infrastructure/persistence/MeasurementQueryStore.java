package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import com.juca.homewatermonitorbackend.session.application.MeasurementSessionView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class MeasurementQueryStore {
    private final JdbcTemplate jdbc;
    public MeasurementQueryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SELECT="""
            select s.*,c.cultivation_id,c.requested_at from tds_session s
            left join tds_device_command c on c.session_id=s.session_id and c.device_id=s.device_id and c.command_type='START'
            """;
    public List<MeasurementSessionView> list(UUID cultivation,boolean unlinked,boolean normal,boolean realOnly,int limit,int offset) {
        String sql=SELECT+" where 1=1";var args=new ArrayList<Object>();
        if(cultivation!=null){sql+=" and c.cultivation_id=?";args.add(cultivation);}
        if(unlinked)sql+=" and c.cultivation_id is null";
        if(normal)sql+=" and s.preservation_result='NORMAL'";
        if(realOnly)sql+=" and s.calibration_version='sen0244-uncalibrated-v1'";
        sql+=" order by coalesce(s.started_at,c.requested_at,s.ingested_at) desc,s.session_id limit ? offset ?";
        args.add(limit);args.add(offset);return jdbc.query(sql,mapper(),args.toArray());
    }
    public MeasurementSessionView get(UUID id) {
        var rows=jdbc.query(SELECT+" where s.session_id=?",mapper(),id);return rows.isEmpty()?null:rows.getFirst();
    }
    private static Instant time(ResultSet r,String name) throws SQLException {
        var t=r.getTimestamp(name);return t==null?null:t.toInstant();
    }
    private static RowMapper<MeasurementSessionView> mapper() {
        return (r,n)->{
            Instant start=time(r,"started_at"),requested=time(r,"requested_at"),ingested=time(r,"ingested_at");
            String version=r.getString("calibration_version");
            String source="simulated-v1".equals(version)?"SIMULATED":
                    "sen0244-uncalibrated-v1".equals(version)?"ADS1115":"UNKNOWN";
            return new MeasurementSessionView(r.getObject("session_id",UUID.class),r.getString("device_id"),
                    r.getObject("cultivation_id",UUID.class),start,requested,ingested,start!=null?start:requested!=null?requested:ingested,
                    start!=null?"MEASURED":requested!=null?"REQUESTED":"INGESTED",r.getString("preservation_result"),
                    r.getInt("preserved_sample_count"),r.getInt("stats_sample_count"),r.getBigDecimal("min_tds_ppm"),
                    r.getBigDecimal("max_tds_ppm"),r.getBigDecimal("avg_tds_ppm"),r.getBigDecimal("median_tds_ppm"),
                    r.getBigDecimal("temperature_assumed_c"),version,source);
        };
    }
}
