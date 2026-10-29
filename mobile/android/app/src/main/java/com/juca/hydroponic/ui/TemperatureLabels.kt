package com.juca.hydroponic.ui

import com.juca.hydroponic.data.SessionSummary
import java.math.BigDecimal
import java.math.RoundingMode

fun degrees(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "°C"

fun temperatureLabel(s: SessionSummary): String {
    val average = s.averageTemperatureC?.let(::degrees) ?: "평균 없음"
    return when (s.temperatureSource) {
        "DS18B20" -> "실측 평균 수온 · $average"
        "SIMULATED" -> "모의 평균 수온 · $average"
        null -> s.temperatureAssumedC?.let { "${degrees(it)} 가정 · 실측 수온 아님" } ?: "가정 수온 기록 없음 · 실측 수온 아님"
        else -> "온도 출처 확인 필요"
    }
}
fun compensationLabel(s: SessionSummary): String? = when (s.compensationVersion) {
    null -> null
    "sen0244-voltage-atc-v1" -> "25°C 기준 온도 보정 · TDS 미교정"
    else -> "보정 공식 확인 필요"
}
