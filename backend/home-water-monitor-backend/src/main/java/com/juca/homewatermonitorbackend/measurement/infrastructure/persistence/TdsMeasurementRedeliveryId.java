package com.juca.homewatermonitorbackend.measurement.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.io.Serializable;
import java.util.UUID;

// 같은 수신 사건 안에서 한 seq를 두 번 기록하지 못하게 하는 복합 식별자다.
@Embeddable
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TdsMeasurementRedeliveryId implements Serializable {
    private UUID redeliveryId;
    private int seq;
}
