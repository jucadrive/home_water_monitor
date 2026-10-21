package com.juca.homewatermonitorbackend.session.application;

import org.springframework.stereotype.Component;

// 기존 정상 전용 진입점은 계속 정확히 60개를 요구한다.
@Component
public class NormalTdsSessionInputValidator {
    private final TdsSessionInputValidator common;

    public NormalTdsSessionInputValidator(TdsSessionInputValidator common) {
        this.common = common;
    }

    public void validate(SaveNormalTdsSessionCommand command) {
        common.validate(command);
        if (command.measurements().size() != 60) {
            throw new IllegalArgumentException("정상 경로에는 원본 60개가 필요합니다.");
        }
    }
}