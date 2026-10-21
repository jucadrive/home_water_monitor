package com.juca.homewatermonitorbackend.measurement.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// DB 매핑과 분리된 측정 원본이다. 업무상 동일 판정에는 equals 대신 compareWith를 사용한다.
public record TdsMeasurement(
        UUID sessionId,
        int seq,
        int elapsedMs,
        BigDecimal tdsPpm,
        boolean isWarmup,
        BigDecimal rawVoltageMv
) {
    // 측정 한 건의 기본 조건만 검사한다. 저장 정밀도·세션 개수는 저장 경로의 책임이다.
    public TdsMeasurement {
        Objects.requireNonNull(sessionId, "sessionId가 필요합니다.");
        Objects.requireNonNull(tdsPpm, "tdsPpm이 필요합니다.");
        if (seq < 1) {
            throw new IllegalArgumentException("seq는 1 이상이어야 합니다.");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs는 음수일 수 없습니다.");
        }
    }

    // 키가 다른 원본은 충돌이 아니라 비교 대상 오류로 거절한다.
    public TdsMeasurementComparisonResult compareWith(TdsMeasurement incoming) {
        Objects.requireNonNull(incoming, "비교할 측정이 필요합니다.");
        if (!sessionId.equals(incoming.sessionId()) || seq != incoming.seq()) {
            throw new IllegalArgumentException("같은 sessionId와 seq를 가진 측정끼리 비교해야 합니다.");
        }

        // 수치의 표현 자릿수는 무시하되 반올림·허용 오차 없이 네 필드를 모두 비교한다.
        boolean identical = tdsPpm.compareTo(incoming.tdsPpm()) == 0
                && elapsedMs == incoming.elapsedMs()
                && isWarmup == incoming.isWarmup()
                && sameOptionalDecimal(rawVoltageMv, incoming.rawVoltageMv());
        return identical ? TdsMeasurementComparisonResult.IDENTICAL : TdsMeasurementComparisonResult.CONFLICT;
    }

    // 전압이 양쪽 모두 없으면 동일하고, 한쪽에만 있으면 충돌이다.
    private static boolean sameOptionalDecimal(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return second == null;
        }
        return second != null && first.compareTo(second) == 0;
    }
}
