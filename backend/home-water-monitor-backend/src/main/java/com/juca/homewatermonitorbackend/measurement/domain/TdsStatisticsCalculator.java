package com.juca.homewatermonitorbackend.measurement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class TdsStatisticsCalculator {

    // 측정값 평균을 계산하고 BigDecimal 값으로 반환하는 메서드
    public BigDecimal average(List<BigDecimal> values) {

        // 목록에 숫자가 하나도 없는지 확인
        if (values.isEmpty()) {
            throw new IllegalArgumentException("측정값이 비어 있습니다.");
        }

        // 합계를 0에서 시작
        BigDecimal sum = BigDecimal.ZERO;

        for (BigDecimal value : values) {
            // 꺼낸 숫자를 합계에 더함
            sum = sum.add(value);
        }

        // 합계를 숫자 개수만큼 나눈다. 단, 결과를 소수 둘째 자리까지 표현하고 소수 셋째 자리에서 반올림
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    // 목록에서 최솟값 찾기
    public BigDecimal minimum(List<BigDecimal> values) {

        // 값이 없으면 계산 중단
        if (values.isEmpty()) {
            throw new IllegalArgumentException("측정값이 비어 있습니다.");
        }

        // 첫 번째 값을 현재 최솟값으로 설정
        BigDecimal minimum = values.getFirst();

        for (BigDecimal value: values) {

            // 꺼낸 값과 현재 최솟값을 비교. < 0은 꺼낸 값이 현재 최솟값보다 작다는 뜻
            if (value.compareTo(minimum) < 0) {
                // 더 작은 값을 새로운 최솟값으로 기억
                minimum = value;
            }
        }

        // 끝까지 확인한 최솟값 반환
        return minimum;
    }

    public BigDecimal maximum(List<BigDecimal> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("측정값이 비어 있습니다.");
        }

        BigDecimal maximum = values.getFirst();

        for (BigDecimal value: values) {
            if (value.compareTo(maximum) > 0) {
                maximum = value;
            }
        }

        return maximum;
    }

    public BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("측정값이 비어 있습니다.");
        }

        // 입력 목록을 복사. 정렬할 때 입력 목록의 순서를 보존
        List<BigDecimal> sorted = new ArrayList<>(values);
        // 숫자 비교 결과를 이용해 오름차순 정렬
        sorted.sort(BigDecimal::compareTo);

        // 가운데 위치 계산. 정수 나눗셈이므로 소수 부분은 버림
        int middle = sorted.size() / 2;

        // 개수를 2로 나눈 나머지가 1이면 홀수
        if (sorted.size() % 2 == 1) {
            // 홀수 개일 때 가운데 값 반환
            return sorted.get(middle);
        }

        // 짝수 개일 때 가운데 왼쪽 값
        BigDecimal left = sorted.get(middle - 1);
        // 짝수 개일 때 가운데 오른쪽 값
        BigDecimal right = sorted.get(middle);

        // 가운데 두 값의 평균을 구하고 소수 둘째 자리까지 반올림
        return left.add(right).divide(
                BigDecimal.valueOf(2),
                2,
                RoundingMode.HALF_UP
        );
    }
}
