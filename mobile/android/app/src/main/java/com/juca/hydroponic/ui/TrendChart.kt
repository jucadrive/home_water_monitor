package com.juca.hydroponic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.data.*
import java.time.Instant

@Composable fun TrendChart(sessions: List<SessionSummary>, maintenance: List<Maintenance>) {
    val data = sessions.filter { it.eligibleForRealTrend() }.sortedBy { Instant.parse(it.displayAt) }
    val color = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.tertiary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TDS 변화", style = MaterialTheme.typography.titleLarge)
            Text("최근 실측 정상 세션 최대 50건 · 중앙값 ppm", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (data.isEmpty()) {
                Text("기록이 자라는 중이에요", style = MaterialTheme.typography.titleLarge)
                Text("실측 정상 기록이 쌓이면 추세를 보여드립니다.", color = Muted)
            } else {
                val assumed = data.any { it.temperatureSource == null }
                val measured = data.any { it.temperatureSource == "DS18B20" }
                Text(when {
                    assumed && measured -> "수온 기준: 가정 수온 · 실측 수온 보정 기록"
                    measured -> "수온 기준: 실측 수온 보정 기록"
                    else -> "수온 기준: 가정 수온 기록"
                }, style = MaterialTheme.typography.bodySmall, color = Muted)
                val low = data.minOf { it.medianTdsPpm!!.toDouble() }
                val high = data.maxOf { it.medianTdsPpm!!.toDouble() }
                val first = Instant.parse(data.first().displayAt).toEpochMilli()
                val last = Instant.parse(data.last().displayAt).toEpochMilli()
                Text("${low.toBigDecimal().stripTrailingZeros().toPlainString()} ~ ${high.toBigDecimal().stripTrailingZeros().toPlainString()} ppm")
                Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "실측 정상 TDS 중앙값 추세, 최저 $low, 최고 $high ppm" }) {
                    val inset = 8.dp.toPx()
                    fun x(time: Long) = if (first == last) size.width / 2 else inset + ((time - first).toDouble() / (last - first)).toFloat() * (size.width - inset * 2)
                    fun y(value: Double) = if (low == high) size.height / 2 else size.height - inset - ((value - low) / (high - low)).toFloat() * (size.height - inset * 2)
                    for (i in 0..2) drawLine(color.copy(alpha = 0.12f), Offset(0f, i * size.height / 2), Offset(size.width, i * size.height / 2))
                    val path = Path()
                    data.forEachIndexed { i, s ->
                        val point = Offset(x(Instant.parse(s.displayAt).toEpochMilli()), y(s.medianTdsPpm!!.toDouble()))
                        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        drawCircle(color, 3.dp.toPx(), point)
                    }
                    drawPath(path, color, style = Stroke(2.dp.toPx()))
                    maintenance.filter { it.type == "REPLACE" && it.replacementScope == "FULL" }.forEach {
                        val time = Instant.parse(it.occurredAt).toEpochMilli()
                        if (time in first..last) drawLine(marker, Offset(x(time), 0f), Offset(x(time), size.height), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    }
                }
                Text("${date(data.first().displayAt)} → ${date(data.last().displayAt)}", style = MaterialTheme.typography.bodySmall)
                Text("점선: 기록된 전체 교체. 실제 측정 시각이 없으면 요청/수신 시각을 사용하며, 작업 전후 관계를 확정하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
