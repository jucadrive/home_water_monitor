package com.juca.homewatermonitorbackend.session.application;

import java.util.Objects;
import java.util.UUID;

// 내부 처리 결과이며 외부 MQTT/API 계약이 아니다. 신규 저장에는 재수신 비교 결과가 없다.
public record NormalTdsSessionProcessingResult(UUID sessionId, Path path,
                                               TdsSessionRedeliveryResult redeliveryResult) {
    public NormalTdsSessionProcessingResult {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(path);
        if (path == Path.SAVED_NEW && redeliveryResult != null
                || path == Path.PROCESSED_REDELIVERY && (redeliveryResult == null
                || !sessionId.equals(redeliveryResult.sessionId()))) {
            throw new IllegalArgumentException("처리 경로와 재수신 결과가 일치해야 합니다.");
        }
    }

    static NormalTdsSessionProcessingResult saved(UUID sessionId) {
        return new NormalTdsSessionProcessingResult(sessionId, Path.SAVED_NEW, null);
    }

    static NormalTdsSessionProcessingResult redelivered(TdsSessionRedeliveryResult result) {
        return new NormalTdsSessionProcessingResult(result.sessionId(), Path.PROCESSED_REDELIVERY, result);
    }

    public String preservationResult() { return "NORMAL"; }

    public int preservedSampleCount() { return 60; }

    public enum Path { SAVED_NEW, PROCESSED_REDELIVERY }
}
