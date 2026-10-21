package com.juca.homewatermonitorbackend.measurement.domain;

// 같은 업무 식별자를 가진 두 측정의 내용 비교 결과다. 신규 여부는 서비스가 판단한다.
public enum TdsMeasurementComparisonResult {
    IDENTICAL,
    CONFLICT
}
