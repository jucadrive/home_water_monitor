package com.juca.homewatermonitorbackend.cultivation.infrastructure.persistence;

import com.juca.homewatermonitorbackend.cultivation.application.CultivationException;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;

/** The receipt and before/after snapshots commit in the same transaction as the resource change. */
@Repository @Transactional(propagation=Propagation.MANDATORY)
public class CareEditStore {
    @PersistenceContext private EntityManager em;
    public void lockKey(UUID key) {
        // Hash collisions only serialize unrelated requests; identity still uses the complete UUID.
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(:key)")
                .setParameter("key",key.getMostSignificantBits()^key.getLeastSignificantBits()).getSingleResult();
    }
    public String replay(UUID key,String path,String body) {
        var rows=em.createNativeQuery("select request_path,request_body,response_body from care_edit_receipt where request_key=:key",Object[].class)
                .setParameter("key",key).getResultList();
        if(rows.isEmpty())return null;
        var row=(Object[])rows.getFirst();
        if(!path.equals(row[0]) || !body.equals(row[1]))throw new CultivationException(409,"IDEMPOTENCY_CONFLICT");
        return (String)row[2];
    }
    public void save(UUID key,String path,String body,String response,String before,String after,Instant at) {
        em.createNativeQuery("""
                insert into care_edit_receipt(request_key,request_path,request_body,response_body,before_body,after_body,created_at)
                values(:key,:path,:body,:response,:before,:after,:at)
                """).setParameter("key",key).setParameter("path",path).setParameter("body",body).setParameter("response",response)
                .setParameter("before",before).setParameter("after",after).setParameter("at",at).executeUpdate();
    }
}
