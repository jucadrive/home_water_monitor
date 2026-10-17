package com.juca.homewatermonitorbackend.measurement.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TdsStatisticsCalculatorTest {

    @Test
    void calculatesAverageOfValues() {

        // 검사에 사용할 숫자 네 개를 준비
        List<BigDecimal> values = List.of(
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("200"),
                new BigDecimal("400")
        );

        // 계산기 생성
        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        // 준비한 숫자의 평균을 계산
        BigDecimal result = calculator.average(values);

        // 결과가 예상한 202.50인지 확인
        assertEquals(new BigDecimal("202.50"), result);
    }

    @Test
    void roundAverageTwoDecimalPlaces() {
        List<BigDecimal> values = List.of(
                new BigDecimal("100.00"),
                new BigDecimal("100.01")
        );

        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        // (100.00 + 100.01) / 2 = 100.005 소수 둘째 자리까지 HALF_UP 반올림 -> 100.01
        BigDecimal result = calculator.average(values);

        assertEquals(new BigDecimal("100.01"), result);
    }

    @Test
    void rejectsEmptyValues() {

        // 값이 없는 빈 목록 생성
        List<BigDecimal> values = List.of();

        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        // 지정한 예외가 발생하는지 검사
        assertThrows(
                IllegalArgumentException.class, // 발생해야 하는 예외 종류
                () -> calculator.average(values)    // 검사할 동작을 전달. JUnit이 이 동작을 실행함
        );
    }

    @Test
    void findsMinimumValue() {
        List<BigDecimal> values = List.of(
                new BigDecimal("200.00"),
                new BigDecimal("100.00"),
                new BigDecimal("400.00"),
                new BigDecimal("110.00")
        );

        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        BigDecimal result = calculator.minimum(values);

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void findsMaximumValue() {
        List<BigDecimal> values = List.of(
                new BigDecimal("200.00"),
                new BigDecimal("100.00"),
                new BigDecimal("400.00"),
                new BigDecimal("110.00")
        );

        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        BigDecimal result = calculator.maximum(values);

        assertEquals(new BigDecimal("400.00"), result);
    }

    @Test
    void findsMedianForOddNumberOfValues() {
        List<BigDecimal> values = List.of(
                new BigDecimal("200.00"),
                new BigDecimal("100.00"),
                new BigDecimal("110.00")
        );

        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        BigDecimal result = calculator.median(values);

        assertEquals(new BigDecimal("110.00"), result);
    }

    @Test
    void findsMedianForEvenNumberOfValues() {
        List<BigDecimal> values = List.of(
                new BigDecimal("200.00"),
                new BigDecimal("100.00"),
                new BigDecimal("400.00"),
                new BigDecimal("110.00")
        );

        TdsStatisticsCalculator calculator = new TdsStatisticsCalculator();

        BigDecimal result = calculator.median(values);

        assertEquals(new BigDecimal("155.00"), result);
    }
}
