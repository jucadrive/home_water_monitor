package com.juca.homewatermonitorbackend.session.application;

import java.util.UUID;

// 내부 처리 결과다. 실제 커밋 이후에만 외부 보존 확인의 재료로 사용할 수 있다.
public record TdsSessionRedeliveryResult(UUID redeliveryId, UUID sessionId, String preservationResult,
                                       int preservedSampleCount, int identicalCount, int conflictCount) {
    public Outcome outcome() {
        return conflictCount == 0 ? Outcome.ALL_IDENTICAL : Outcome.HAS_CONFLICT;
    }

    public enum Outcome { ALL_IDENTICAL, HAS_CONFLICT }
}
