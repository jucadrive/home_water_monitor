package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.TdsSessionLockAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.stubbing.Answer;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;

// 테스트 전용 spy로 미커밋 시점을 제어한다. 실제 서비스에 테스트 훅을 넣지 않는다.
abstract class IncompleteConcurrencySupport extends IncompleteIntegrationSupport {
    @MockitoSpyBean SaveNormalTdsSessionService normalSaving;
    @MockitoSpyBean SaveIncompleteTdsSessionService incompleteSaving;
    @MockitoSpyBean ProcessIncompleteTdsSessionRedeliveryService comparing;
    @MockitoSpyBean TdsSessionLockAdapter locking;

    HeldCall hold(ExecutorService workers, SaveNormalTdsSessionCommand command, boolean rollback, boolean existing)
            throws Exception {
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contenderPid = new CompletableFuture<Integer>();
        var first = new AtomicBoolean(true);
        Answer<Object> pause = call -> {
            var incoming = call.getArgument(0, SaveNormalTdsSessionCommand.class);
            boolean pauseThis = incoming.sessionId().equals(command.sessionId()) && first.compareAndSet(true, false);
            Object result = call.callRealMethod();
            if (pauseThis) {
                ready.countDown();
                await(release);
                if (rollback) throw new IllegalStateException("test_incomplete_rollback");
            }
            return result;
        };
        if (existing) {
            ProcessIncompleteTdsSessionRedeliveryService target = getUltimateTargetObject(comparing);
            doAnswer(pause).when(target).process(any());
        } else if (command.measurements().size() == 60) {
            SaveNormalTdsSessionService target = getUltimateTargetObject(normalSaving);
            doAnswer(pause).when(target).save(any());
        } else {
            SaveIncompleteTdsSessionService target = getUltimateTargetObject(incompleteSaving);
            doAnswer(pause).when(target).save(any());
        }
        TdsSessionLockAdapter lockTarget = getUltimateTargetObject(locking);
        doAnswer(call -> {
            UUID id = call.getArgument(0);
            // 행 잠금은 조회 안에서 대기하므로 조회 전에 동일 트랜잭션의 PID를 관측한다.
            if (id.equals(command.sessionId()) && ready.getCount() == 0 && release.getCount() == 1) {
                contenderPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            }
            return call.callRealMethod();
        }).when(lockTarget).lock(any());
        var future = workers.submit(() -> processing.process(command));
        if (!ready.await(8, TimeUnit.SECONDS)) {
            release.countDown();
            fail("미커밋 상태 준비 실패");
        }
        return new HeldCall(future, release, contenderPid);
    }

    void awaitDatabaseWait(HeldCall held, boolean insert) throws Exception {
        int pid = held.contenderPid().get(5, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock'
                    and (not ? or query ilike 'insert%'))
                    """, Boolean.class, pid, insert))) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("실제 DB 잠금 대기를 관측하지 못했습니다.");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 대기 한도 초과");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 대기 중단", error);
        }
    }

    record HeldCall(Future<TdsSessionProcessingResult> future, CountDownLatch release,
                    CompletableFuture<Integer> contenderPid) implements AutoCloseable {
        @Override public void close() { release.countDown(); }
    }
}
