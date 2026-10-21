package com.juca.homewatermonitorbackend.session.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

// 독립 DB 연결의 실제 잠금 대기를 관측해 시간 추측만으로 성공 판정하지 않는다.
@Timeout(25)
class TdsSessionRedeliveryConcurrencyIntegrationTest extends RedeliveryIntegrationSupport {
    @Test
    void serializesTwoCallsForTheSameSessionAndRecordsBothEvents() throws Exception {
        var c = storedCommand();
        var before = snapshot(c.sessionId());
        var firstReady = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondPid = new CompletableFuture<Integer>();
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<TdsSessionRedeliveryResult> first = workers.submit(() -> transaction().execute(status -> {
                var result = redelivery.process(c);
                firstReady.countDown();
                await(release);
                return result;
            }));
            try {
                assertTrue(firstReady.await(8, TimeUnit.SECONDS));
                Future<TdsSessionRedeliveryResult> second = workers.submit(() -> transaction().execute(status -> {
                    secondPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    return redelivery.process(c);
                }));
                awaitDatabaseLock(secondPid.get(5, TimeUnit.SECONDS));
                assertFalse(second.isDone());
                assertEquals(0, headers.countBySessionId(c.sessionId()));
                release.countDown();
                assertNotEquals(first.get(8, TimeUnit.SECONDS).redeliveryId(), second.get(8, TimeUnit.SECONDS).redeliveryId());
            } finally {
                release.countDown();
            }
        }
        assertEquals(2, headers.countBySessionId(c.sessionId()));
        assertEquals(120, detailCount(c.sessionId()));
        assertEquals(120, headers.totalsBySessionId(c.sessionId()).getIdenticalCount());
        assertUnchanged(c.sessionId(), before);
    }

    @Test
    void allowsAnotherSessionWhileOneSessionIsLocked() throws Exception {
        var blocked = storedCommand();
        var independent = storedCommand();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<?> holder = holdLock(workers, blocked.sessionId(), locked, release);
            try {
                assertTrue(locked.await(8, TimeUnit.SECONDS));
                var independentCall = workers.submit(() -> redelivery.process(independent));
                assertEquals(60, independentCall.get(5, TimeUnit.SECONDS).identicalCount());
                assertEquals(1, headers.countBySessionId(independent.sessionId()));
                assertEquals(1, release.getCount());
            } finally {
                release.countDown();
                holder.get(8, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void timesOutWithoutDiagnosticsAndSucceedsAfterLockRelease() throws Exception {
        var c = storedCommand();
        var before = snapshot(c.sessionId());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> holder = holdLock(worker, c.sessionId(), locked, release);
            try {
                assertTrue(locked.await(8, TimeUnit.SECONDS));
                var started = System.nanoTime();
                var error = assertThrows(RuntimeException.class, () -> redelivery.process(c));
                var sql = assertInstanceOf(SQLException.class, NestedExceptionUtils.getMostSpecificCause(error));
                assertEquals("55P03", sql.getSQLState());
                assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(8)) < 0);
                assertEquals(0, headers.countBySessionId(c.sessionId()));
                assertEquals(0, detailCount(c.sessionId()));
                assertUnchanged(c.sessionId(), before);
            } finally {
                release.countDown();
                holder.get(8, TimeUnit.SECONDS);
            }
        }
        assertEquals(60, redelivery.process(c).identicalCount());
        assertEquals(1, headers.countBySessionId(c.sessionId()));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    private Future<?> holdLock(ExecutorService worker, UUID id, CountDownLatch locked, CountDownLatch release) {
        return worker.submit(() -> transaction().executeWithoutResult(status -> {
            jdbc.queryForObject("select session_id from tds_session where session_id = ? for update", UUID.class, id);
            locked.countDown();
            await(release);
        }));
    }

    private void awaitDatabaseLock(int pid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')
                    """, Boolean.class, pid);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            // 조회 간격만 조절하고 실제 판정에는 DB의 잠금 대기 상태를 사용한다.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("두 번째 트랜잭션의 DB 잠금 대기를 관측하지 못했습니다.");
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
}
