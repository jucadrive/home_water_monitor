package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionLockAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;

// 테스트 동기화는 Spring Bean spy에 한정하며 실제 서비스에 대기 훅을 추가하지 않는다.
abstract class RoutingConcurrencySupport extends RedeliveryIntegrationSupport {
    @Autowired ProcessNormalTdsSessionService routing;
    @MockitoSpyBean SaveNormalTdsSessionService saving;
    @MockitoSpyBean TdsSessionLockAdapter locking;

    HeldSave holdFirstSave(ExecutorService workers, SaveNormalTdsSessionCommand command, boolean rollback)
            throws Exception {
        var saved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contenderPid = new CompletableFuture<Integer>();
        var first = new AtomicBoolean(true);
        // stubbing 자체가 MANDATORY 프록시를 호출하지 않도록 내부 spy를 설정한다.
        SaveNormalTdsSessionService saveTarget = getUltimateTargetObject(saving);
        TdsSessionLockAdapter lockTarget = getUltimateTargetObject(locking);
        doAnswer(call -> {
            var incoming = call.getArgument(0, SaveNormalTdsSessionCommand.class);
            boolean hold = incoming.sessionId().equals(command.sessionId()) && first.compareAndSet(true, false);
            Object result = call.callRealMethod();
            if (hold) {
                saved.countDown();
                await(release);
                if (rollback) {
                    throw new IllegalStateException("test_first_save_rollback");
                }
            }
            return result;
        }).when(saveTarget).save(any());
        doAnswer(call -> {
            var result = call.callRealMethod();
            UUID id = call.getArgument(0);
            if (id.equals(command.sessionId()) && saved.getCount() == 0 && release.getCount() == 1) {
                contenderPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            }
            return result;
        }).when(lockTarget).lock(any());
        var future = workers.submit(() -> routing.process(command));
        if (!saved.await(8, TimeUnit.SECONDS)) {
            release.countDown();
            fail("최초 저장의 미커밋 상태를 준비하지 못했습니다.");
        }
        return new HeldSave(future, release, contenderPid);
    }

    void awaitInsertWait(HeldSave held) throws Exception {
        int pid = held.contenderPid().get(5, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var waiting = jdbc.queryForObject("""
                    select exists(select 1 from pg_stat_activity
                    where pid = ? and wait_event_type = 'Lock' and query ilike 'insert%')
                    """, Boolean.class, pid);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("경합 요청의 실제 INSERT 잠금 대기를 관측하지 못했습니다.");
    }

    long sessionCount(UUID id) {
        return jdbc.queryForObject("select count(*) from tds_session where session_id = ?", Long.class, id);
    }

    void assertNoNewData(UUID id) {
        assertEquals(0, sessionCount(id));
        assertEquals(0L, jdbc.queryForObject("select count(*) from tds_measurement where session_id = ?", Long.class, id));
        assertEquals(0, headers.countBySessionId(id));
        assertEquals(0, detailCount(id));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(12, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 잠금 해제 대기 한도 초과");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 대기 중단", error);
        }
    }

    record HeldSave(Future<NormalTdsSessionProcessingResult> future, CountDownLatch release,
                    CompletableFuture<Integer> contenderPid) implements AutoCloseable {
        @Override
        public void close() { release.countDown(); }
    }
}
