package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun date(value: String?): String = value?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")) } ?: "기록 없음"
fun ppm(value: BigDecimal?): String = value?.stripTrailingZeros()?.toPlainString()?.plus(" ppm") ?: "측정 기록 없음"

@Composable fun DashboardScreen(s: MonitorState, vm: MonitorViewModel) {
    LazyColumn(Modifier.fillMaxSize().testTag("dashboard-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { GardenIntro("오늘도, 초록 한 스푼", "작은 돌봄이 모여 나의 정원이 자라요.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Leaf)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("오늘의 재배 기록", style = MaterialTheme.typography.titleLarge)
                    Text("영양액의 변화를 차곡차곡 살펴보세요.", color = Forest)
                    GardenBadge("불러온 재배 ${s.cultivations.size}개 · 재배 중 ${s.cultivations.count { it.cultivation.status == "ACTIVE" }}개")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::createForm, enabled = !s.busy && s.pending == null, modifier = Modifier.weight(1f)) { Text("재배 등록") }
                OutlinedButton(onClick = { vm.list() }, enabled = !s.busy) { Text("새로고침") }
            }
        }
        item { GardenSection("나의 초록 친구들", "식물마다 영양액과 돌봄 기록을 따로 모아요") }
        if (s.cultivations.isEmpty()) item {
            GardenEmpty("첫 초록 친구를 기다려요", "등록한 재배가 없습니다. 첫 영양액 용기의 재배를 등록하세요.")
        }
        items(s.cultivations, key = { it.cultivation.id }) { overview ->
            val c = overview.cultivation
            Card(onClick = { vm.detail(c.id) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GardenBadge(c.plantName + if (c.status == "ENDED") " · 종료" else " · 재배 중", warm = c.status == "ENDED")
                            Text(c.name, style = MaterialTheme.typography.headlineMedium)
                        }
                        Sprout(Modifier.size(64.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("최근 실측 중앙값", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(ppm(overview.latestMeasurement?.medianTdsPpm), style = if (overview.latestMeasurement?.medianTdsPpm == null) MaterialTheme.typography.titleLarge else NumberStyle)
                    Text("마지막 전체 교체  ${date(overview.lastFullReplacementAt)}")
                    Text("재배 시작  ${date(c.startedAt)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (s.moreCultivations) item { OutlinedButton(onClick = { vm.list(true) }, enabled = !s.busy) { Text("재배 더 보기") } }
        item { TextButton(onClick = { vm.unlinked() }, enabled = !s.busy) { Text("재배에 연결되지 않은 측정 기록") } }
        if (s.command != null) item { TextButton(onClick = vm::commandScreen, enabled = !s.busy) { Text("최근 측정 요청 상태") } }
        item { Text("TDS는 농도 변화의 참고 기록입니다. 식물 건강이나 교체 필요성을 확정하지 않습니다.", style = MaterialTheme.typography.bodySmall) }
    }
}
