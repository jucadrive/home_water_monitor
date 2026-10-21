# 다음 기능 후보 — 측정 도메인 비교

> 2026-10-21: 아래 코드는 미구현 설계 자료다. 기존 사용자 직접 입력·실행 방식은 종료했다. 기능 착수 시 AI가 실제 소스·Git을 확인하고 작업 명세와 인수 조건을 작성한 후 최초 계획 승인을 받아 구현·검증한다. 이번 하네스 구축에는 이 기능 구현을 포함하지 않는다.

## 합의된 비교 기준

같은 `sessionId + seq`에서 `tdsPpm`·`elapsedMs`·`isWarmup`·`rawVoltageMv` 네 필드를 모두 비교한다. 이는 사용자가 선택한 정책이므로 다시 선택을 요구하지 않는다.

- 네 필드가 모두 같으면 동일 재수신, 하나라도 다르면 내용 충돌.
- 수치는 `BigDecimal.compareTo()`로 비교한다. `151.3`과 `151.30`은 동일하며 반올림·허용 오차를 추가하지 않는다.
- 전압은 양쪽 null이면 동일, 한쪽만 null이면 충돌.
- 다른 sessionId·seq는 서로 다른 측정이므로 이 메서드의 내용 비교 대상 오류로 다룬다.
- 신규 여부는 서비스가 기존 원본 조회 결과로 판정한다. 비교 결과 enum에는 NEW를 넣지 않는다.
- 진단 기록의 필드·메시지/샘플 단위·카운터·동시성, 세션 메타데이터 변경 판정은 다음 검토 대상이다.

## 1. 비교 결과 enum

파일: `backend/home-water-monitor-backend/src/main/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurementComparisonResult.java`

```java
package com.juca.homewatermonitorbackend.measurement.domain;

// 같은 식별자를 가진 두 측정의 내용 비교 결과
public enum TdsMeasurementComparisonResult {

    // 비교 대상 네 필드가 모두 동일
    IDENTICAL,

    // 비교 대상 필드 중 하나 이상이 다름
    CONFLICT
}
```

신규 여부는 서비스의 조회 결과로 판단하고, 이 enum은 기존 원본과 수신 원본의 내용 비교 결과만 표현한다.

## 2. 측정 도메인 객체

파일: `backend/home-water-monitor-backend/src/main/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurement.java`

```java
package com.juca.homewatermonitorbackend.measurement.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// 측정 한 건의 정보와 동일·충돌 판단 규칙을 표현
public record TdsMeasurement(
        UUID sessionId,
        int seq,
        int elapsedMs,
        BigDecimal tdsPpm,
        boolean isWarmup,
        BigDecimal rawVoltageMv
) {

    // 도메인 객체가 반드시 가져야 하는 기본 조건 확인
    public TdsMeasurement {
        Objects.requireNonNull(sessionId, "sessionId가 필요합니다.");
        Objects.requireNonNull(tdsPpm, "tdsPpm이 필요합니다.");

        if (seq < 1) {
            throw new IllegalArgumentException("seq는 1 이상이어야 합니다.");
        }

        if (elapsedMs < 0) {
            throw new IllegalArgumentException(
                    "elapsedMs는 음수일 수 없습니다."
            );
        }
    }

    // 기존 원본을 기준으로 새로 수신한 측정의 내용을 비교
    public TdsMeasurementComparisonResult compareWith(
            TdsMeasurement incoming
    ) {
        Objects.requireNonNull(incoming, "비교할 측정이 필요합니다.");

        // 같은 업무 식별자를 가진 측정끼리만 내용 비교 가능
        if (!sessionId.equals(incoming.sessionId())
                || seq != incoming.seq()) {
            throw new IllegalArgumentException(
                    "같은 sessionId와 seq를 가진 측정끼리 비교해야 합니다."
            );
        }

        // 숫자는 표현 자릿수가 아닌 실제 수치로 비교
        boolean identical =
                tdsPpm.compareTo(incoming.tdsPpm()) == 0
                && elapsedMs == incoming.elapsedMs()
                && isWarmup == incoming.isWarmup()
                && sameOptionalDecimal(
                        rawVoltageMv,
                        incoming.rawVoltageMv()
                );

        return identical
                ? TdsMeasurementComparisonResult.IDENTICAL
                : TdsMeasurementComparisonResult.CONFLICT;
    }

    // 선택 수치의 null 여부와 실제 수치를 함께 비교
    private static boolean sameOptionalDecimal(
            BigDecimal first,
            BigDecimal second
    ) {
        // 양쪽 모두 null이면 동일
        if (first == null) {
            return second == null;
        }

        // 한쪽만 null이면 다르고, 양쪽에 값이 있으면 수치 비교
        return second != null && first.compareTo(second) == 0;
    }
}
```

기존 `TdsMeasurementEntity`는 DB 매핑을, 이번 `TdsMeasurement`는 업무 비교 규칙을 담당한다. 도메인 객체는 Spring·JPA에 의존하지 않는다. record가 자동 생성한 `equals()`는 BigDecimal의 소수 자릿수까지 비교하므로 업무상 동일 판정에는 `compareWith()`를 사용한다.

## 3. 비교 단위 테스트

파일: `backend/home-water-monitor-backend/src/test/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurementTest.java`

```java
package com.juca.homewatermonitorbackend.measurement.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Spring과 DB 없이 측정 비교 규칙만 검증
class TdsMeasurementTest {

    // 각 테스트에서 같은 업무 식별자를 만들기 위한 고정 세션 ID
    private static final UUID SESSION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void identifiesSameContentRegardlessOfDecimalScale() {
        // 표현 자릿수만 다르고 실제 수치와 나머지 필드는 동일
        TdsMeasurement original =
                sample(1_000, "151.3", false, "750.0");

        TdsMeasurement incoming =
                sample(1_000, "151.30", false, "750.00");

        assertEquals(
                TdsMeasurementComparisonResult.IDENTICAL,
                original.compareWith(incoming)
        );
    }

    @Test
    void identifiesSameContentWhenBothVoltagesAreMissing() {
        // 선택값인 전압이 양쪽 모두 없으면 동일
        TdsMeasurement original =
                sample(1_000, "151.3", false, null);

        TdsMeasurement incoming =
                sample(1_000, "151.30", false, null);

        assertEquals(
                TdsMeasurementComparisonResult.IDENTICAL,
                original.compareWith(incoming)
        );
    }

    @Test
    void detectsConflictWhenAnyComparedFieldDiffers() {
        TdsMeasurement original =
                sample(1_000, "151.3", false, "750.0");

        // 기준 원본에서 각각 한 필드만 변경
        List<TdsMeasurement> changedMeasurements = List.of(
                sample(1_000, "151.4", false, "750.0"), // TDS 변경
                sample(2_000, "151.3", false, "750.0"), // 경과시간 변경
                sample(1_000, "151.3", true, "750.0"),  // 안정화 여부 변경
                sample(1_000, "151.3", false, "751.0")  // 전압 변경
        );

        for (TdsMeasurement incoming : changedMeasurements) {
            assertEquals(
                    TdsMeasurementComparisonResult.CONFLICT,
                    original.compareWith(incoming),
                    "충돌로 판단해야 하는 측정: " + incoming
            );
        }
    }

    @Test
    void detectsConflictWhenOnlyOneVoltageIsMissing() {
        TdsMeasurement withoutVoltage =
                sample(1_000, "151.3", false, null);

        TdsMeasurement withVoltage =
                sample(1_000, "151.3", false, "750.0");

        // 기존과 수신 중 어느 쪽에 값이 없더라도 충돌
        assertEquals(
                TdsMeasurementComparisonResult.CONFLICT,
                withoutVoltage.compareWith(withVoltage)
        );

        assertEquals(
                TdsMeasurementComparisonResult.CONFLICT,
                withVoltage.compareWith(withoutVoltage)
        );
    }

    @Test
    void rejectsComparisonBetweenDifferentSessions() {
        TdsMeasurement original =
                sample(1_000, "151.3", false, "750.0");

        // 내용이 같아도 세션 ID가 다르면 별개의 측정
        TdsMeasurement anotherSession = new TdsMeasurement(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                1,
                1_000,
                new BigDecimal("151.3"),
                false,
                new BigDecimal("750.0")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> original.compareWith(anotherSession)
        );
    }

    @Test
    void rejectsComparisonBetweenDifferentSequences() {
        TdsMeasurement original =
                sample(1_000, "151.3", false, "750.0");

        // 같은 세션이라도 seq가 다르면 별개의 측정
        TdsMeasurement anotherSequence = new TdsMeasurement(
                SESSION_ID,
                2,
                1_000,
                new BigDecimal("151.3"),
                false,
                new BigDecimal("750.0")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> original.compareWith(anotherSequence)
        );
    }

    // 공통 식별자를 사용하고 비교할 내용만 바꿔 테스트 데이터 생성
    private TdsMeasurement sample(
            int elapsedMs,
            String ppm,
            boolean isWarmup,
            String voltage
    ) {
        return new TdsMeasurement(
                SESSION_ID,
                1,
                elapsedMs,
                new BigDecimal(ppm),
                isWarmup,
                voltage == null ? null : new BigDecimal(voltage)
        );
    }
}
```

마지막 두 테스트는 서비스가 잘못된 원본끼리 비교하도록 연결했을 때 예외로 알아차리기 위한 검증이다. 내용 충돌은 같은 측정 식별자 안에서 판단한다.

## 실행 결과 읽기와 이후 단계

구현 승인 후 AI가 `TdsMeasurementTest` 클래스 검증을 실행한다. CLI에서는 실제 Gradle 프로젝트 폴더에서 다음 명령을 사용한다.

```powershell
# 사용자가 세 파일을 작성한 뒤 비교 단위 테스트만 실행
./gradlew.bat test --tests "com.juca.homewatermonitorbackend.measurement.domain.TdsMeasurementTest"
```

기대 결과는 테스트 6개 통과·실패 0개다. 현재 이 결과를 확인한 상태는 아니다.

통과 후에는 동일 재수신·내용 충돌 진단에 저장할 정보와 메시지/샘플 기록 단위·카운터를 함께 정하고, 기존 원본 조회 → 도메인 비교 → 결과별 저장 경로에 연결한다. 기존 원본과 통계 보존·목록 단위 원자성·커밋 후 MQTT 확인 합의는 유지한다.
