package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.*
import com.juca.hydroponic.data.Maintenance
import com.juca.hydroponic.data.SessionSummary
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun maintenanceLabel(m: Maintenance): String = when (m.type) {
    "REPLACE" -> if (m.replacementScope == "FULL") "전체 교체" else "부분 교체"
    "TOP_UP" -> "물 보충"
    else -> "영양액·영양제 추가"
}
fun basis(value: String): String = when (value) { "MEASURED" -> "측정 시각"; "REQUESTED" -> "측정 요청 시각"; else -> "서버 수신 시각" }
@Composable fun DetailScreen(s: MonitorState, vm: MonitorViewModel) {
    val data = s.detail ?: return
    val c = data.cultivation
    var ending by rememberSaveable { mutableStateOf(false) }
    val active = c.status == "ACTIVE"
    val end = c.endedAt?.let(Instant::parse) ?: Instant.now()
    val days = ChronoUnit.DAYS.between(Instant.parse(c.startedAt).atZone(ZoneId.systemDefault()).toLocalDate(), end.atZone(ZoneId.systemDefault()).toLocalDate())
    LazyColumn(Modifier.fillMaxSize().testTag("detail-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GardenBadge(c.plantName + if (active) " · 재배 중" else " · 종료", warm = !active)
            GardenIntro(c.name, if (active) "재배 ${days + 1}일째" else "종료한 재배 · ${days + 1}일간", "나의 식물 일지")
            Text("시작 ${date(c.startedAt)}", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (!active) Text("종료 ${date(c.endedAt)}", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (c.memo.isNotBlank()) Text(c.memo, Modifier.padding(top = 8.dp))
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("최근 실측 중앙값", style = MaterialTheme.typography.labelLarge)
                Text(ppm(data.latestMeasurement?.medianTdsPpm), style = if (data.latestMeasurement?.medianTdsPpm == null) MaterialTheme.typography.headlineSmall else NumberStyle)
                Text("마지막 전체 교체  ${date(data.lastFullReplacementAt)}")
                data.latestMeasurement?.let { m ->
                    Text(temperatureLabel(m), style = MaterialTheme.typography.bodySmall)
                }
            } }
        }
        item { TrendChart(s.trend, s.maintenance) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::start, enabled = active && !s.busy && s.pending == null && s.device.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("${s.device.ifBlank { "장치 설정 필요" }} · 측정 시작") }
                OutlinedButton(onClick = vm::maintenanceForm, enabled = active && !s.busy && s.pending == null, modifier = Modifier.fillMaxWidth()) { Text("물·영양액 관리 기록") }
                TextButton(onClick = { vm.detail(c.id) }, enabled = !s.busy) { Text("기록 새로고침") }
            }
        }
        item { GardenSection("관리 타임라인", "물 한 번, 돌봄 한 번의 기록") }
        if (s.maintenance.isEmpty()) item { Text("아직 관리 기록이 없습니다.", color = Muted) }
        items(s.maintenance, key = { "m-${it.id}" }) { m ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(maintenanceLabel(m), color = MaterialTheme.colorScheme.primary)
                Text(date(m.occurredAt)); Text(m.amountMl?.toPlainString()?.plus(" ml") ?: "작업량 미상")
                if (m.memo.isNotBlank()) Text(m.memo)
            } }
        }
        if (s.moreMaintenance) item { TextButton(onClick = { vm.moreHistory(true) }, enabled = !s.busy) { Text("관리 이력 더 보기") } }
        item { GardenSection("측정 이력", "영양액의 변화를 살펴봐요") }
        if (s.sessions.isEmpty()) item { Text("아직 보존된 측정이 없습니다.") }
        items(s.sessions, key = { "s-${it.sessionId}" }) { session -> SessionCard(session, !s.busy) { vm.session(session.sessionId) } }
        if (s.moreSessions) item { TextButton(onClick = { vm.moreHistory(false) }, enabled = !s.busy) { Text("측정 이력 더 보기") } }
        if (active) item { TextButton(onClick = { ending = true }, enabled = !s.busy && s.pending == null) { Text("재배 종료") } }
    }
    if (ending) AlertDialog(onDismissRequest = { ending = false }, title = { Text("재배를 종료할까요?") },
        text = { Text("새 측정·관리 등록을 막습니다. 이미 요청한 측정의 결과는 계속 보존합니다.") },
        confirmButton = { TextButton(onClick = { ending = false; vm.end() }) { Text("종료") } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text("취소") } })
}
@Composable fun SessionCard(s: SessionSummary, enabled: Boolean, click: () -> Unit) {
    Card(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (s.preservationResult == "NORMAL") ppm(s.medianTdsPpm) else "불완전 측정 · 통계 없음", style = MaterialTheme.typography.titleMedium)
            Text("${date(s.displayAt)} · ${basis(s.timeBasis)}")
            Text("원본 ${s.preservedSampleCount}/60 · ${s.source}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
