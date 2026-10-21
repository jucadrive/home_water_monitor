package com.juca.homewatermonitorbackend.session.application;

import java.util.Objects;
import java.util.UUID;

// 정상/불완전의 내부 보존 결과다. 실제 MQTT 응답 형식은 후속 계약이다.
public record TdsSessionProcessingResult(UUID sessionId, String preservationResult, int preservedSampleCount,
                                         Path path, TdsSessionRedeliveryResult redeliveryResult) {
    public TdsSessionProcessingResult {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(path);
        if (!("NORMAL".equals(preservationResult) && preservedSampleCount == 60
                || "INCOMPLETE".equals(preservationResult) && preservedSampleCount >= 0 && preservedSampleCount < 60)) {
            throw new IllegalArgumentException("보존 결과와 건수가 일치해야 합니다.");
        }
        if (path == Path.SAVED_NEW && redeliveryResult != null
                || path == Path.PROCESSED_REDELIVERY && (redeliveryResult == null
                || !sessionId.equals(redeliveryResult.sessionId())
                || !preservationResult.equals(redeliveryResult.preservationResult())
                || preservedSampleCount != redeliveryResult.preservedSampleCount())) {
            throw new IllegalArgumentException("처리 경로와 재수신 결과가 일치해야 합니다.");
        }
    }

    static TdsSessionProcessingResult saved(UUID id, int count) {
        return new TdsSessionProcessingResult(id, count == 60 ? "NORMAL" : "INCOMPLETE", count, Path.SAVED_NEW, null);
    }

    static TdsSessionProcessingResult redelivered(TdsSessionRedeliveryResult result) {
        return new TdsSessionProcessingResult(result.sessionId(), result.preservationResult(),
                result.preservedSampleCount(), Path.PROCESSED_REDELIVERY, result);
    }

    public enum Path { SAVED_NEW, PROCESSED_REDELIVERY }
}
