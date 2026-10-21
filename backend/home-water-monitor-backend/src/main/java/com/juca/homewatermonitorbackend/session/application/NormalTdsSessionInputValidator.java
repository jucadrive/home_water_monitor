package com.juca.homewatermonitorbackend.session.application;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

// 신규 저장과 정상 세션 재수신에 공통인 목록 형식·DB 저장 범위를 검증한다.
@Component
public class NormalTdsSessionInputValidator {
    private static final int EXPECTED_SAMPLE_COUNT = 60;
    private static final int SAMPLING_INTERVAL_MS = 1_000;
    // 두 정상 경로가 공유하는 기본 입력 조건을 검증한다.
    public void validate(SaveNormalTdsSessionCommand command) {

        // 요청과 세션 식별자는 반드시 필요
        if (command == null || command.sessionId() == null) {
            throw new IllegalArgumentException("세션 ID가 필요합니다.");
        }

        // 필수 메타데이터가 현재 DB 컬럼에 저장 가능한지 확인
        requireText(command.deviceId(), 64, "deviceId");
        requireText(command.calibrationVersion(), 64, "calibrationVersion");
        requireDecimal(command.temperatureAssumedC(), 5, "temperatureAssumedC");

        // 현재 정상 저장 경로는 1초 간격 측정만 처리
        if (command.samplingIntervalMs() != SAMPLING_INTERVAL_MS) {
            throw new IllegalArgumentException("첫 정상 저장 경로의 측정 간격은 1000ms입니다.");
        }

        // 불완전 목록은 이후 별도 보존 경로에서 처리
        if (command.measurements().size() != EXPECTED_SAMPLE_COUNT) {
            throw new IllegalArgumentException("첫 정상 저장 경로에는 원본 60개가  필요합니다.");
        }

        // 이미 등장한 seq를 기억해 목록 내부 중복 검사
        Set<Integer> sequences = new HashSet<>();

        for (SaveNormalTdsSessionCommand.Measurement measurement : command.measurements()) {

            // 개수뿐 아니라 필요한 순번 범위도 확인
            if (measurement.seq() < 1 || measurement.seq() > EXPECTED_SAMPLE_COUNT) {
                throw new IllegalArgumentException("seq는 1부터 60까지여야 합니다.");
            }

            // Set.add()는 같은 값이 이미 존재하면 false 반환
            if (!sequences.add(measurement.seq())) {
                throw new IllegalArgumentException("목록 안에 중복 seq가 있습니다: " + measurement.seq());
            }

            // 측정 시작 이후 경과시간은 음수일 수 없음
            if (measurement.elapsedMs() < 0) {
                throw new IllegalArgumentException("elapsedMs는 음수일 수 없습니다.");
            }

            // TDS는 필수이며 NUMERIC(9, 2)에 저장 가능해야 함
            requireDecimal(measurement.tdsPpm(), 9, "tdsPpm");

            // 원본 전압은 선택값이므로 존재할 때만 검사
            if (measurement.rawVoltageMv() != null) {
                requireDecimal(measurement.rawVoltageMv(), 9, "rawVoltageMv");
            }
        }
    }

    // 필수 문자열의 공백 여부와 DB 컬럼 길이 검사
    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "는 비어 있지 않은 " + maxLength + "자 이하 문자열이어야 합니다.");
        }
    }

    // 현재 DB의 NUMERIC(precision, 2)에 저장 가능한 수치인지 검사
    private void requireDecimal(BigDecimal value, int precision, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "가 필요합니다.");
        }

        // 151.300처럼 불필요한 끝자리 0은 허용하고 실제 소수 자릿수 검사
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(field + "는 소수 둘째 자리까지 표현할 수 있습니다.");
        }

        // 소수 두 자리를 제외한 정수부의 허용 범위 계산
        BigDecimal limit = BigDecimal.TEN.pow(precision - 2);

        // 양수와 음수 모두 절댓값으로 저장 범위를 확인한다.
        if (value.abs().compareTo(limit) >= 0) {
            throw new IllegalArgumentException(field + "가 DB에 저장 가능한 범위를 벗어났습니다.");
        }
    }
}
