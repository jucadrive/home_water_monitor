package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.*
import com.juca.hydroponic.data.SessionDetail

@Composable fun UnlinkedScreen(s: MonitorState, vm: MonitorViewModel) {
    LazyColumn(Modifier.fillMaxSize().testTag("unlinked-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GardenIntro("미연결 측정 기록", "재배에 연결되기 전의 기록도 원본과 함께 보존합니다.", "기록 보관함")
            TextButton(onClick = { vm.unlinked() }, enabled = !s.busy) { Text("새로고침") }
        }
        if (s.unlinked.isEmpty()) item { Text("재배에 연결되지 않은 측정이 없습니다.") }
        items(s.unlinked, key = { it.sessionId }) { m -> SessionCard(m, !s.busy) { vm.session(m.sessionId) } }
        if (s.moreUnlinked) item { TextButton(onClick = { vm.unlinked(true) }, enabled = !s.busy) { Text("측정 더 보기") } }
    }
}
@Composable fun SessionScreen(data: SessionDetail?) {
    if (data == null) return
    val s = data.session
    LazyColumn(Modifier.fillMaxSize().testTag("session-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GardenIntro("측정 결과", "영양액의 작은 변화를 기록했어요.", "측정 노트")
            GardenBadge(if (s.preservationResult == "NORMAL") "정상 보존" else "불완전 보존 · 통계 제외", warm = s.preservationResult != "NORMAL")
            Text(ppm(s.medianTdsPpm), style = if (s.medianTdsPpm == null) MaterialTheme.typography.headlineMedium else NumberStyle)
            if (s.preservationResult == "NORMAL") Text("최소 ${ppm(s.minTdsPpm)} · 평균 ${ppm(s.avgTdsPpm)} · 최대 ${ppm(s.maxTdsPpm)}")
            Text("${date(s.displayAt)} · ${basis(s.timeBasis)}")
            Text("${s.deviceId} · ${s.source}")
            Text("원본 ${s.preservedSampleCount}/60 · 통계 ${s.statsSampleCount}개")
            Text("가정 수온 ${s.temperatureAssumedC?.toPlainString()}°C · 실측 수온 아님")
            Text("교정 정보 ${s.calibrationVersion}", style = MaterialTheme.typography.bodySmall)
            Text("세션 ${s.sessionId}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Text("보존 원본", style = MaterialTheme.typography.titleLarge)
            Text("안정화 원본도 보존하며 정상 통계에서는 제외합니다.", style = MaterialTheme.typography.bodySmall)
        }
        if (data.measurements.isEmpty()) item { Text("보존된 원본이 없습니다.") }
        items(data.measurements, key = { it.seq }) { m ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("#${m.seq} · ${m.elapsedMs} ms · ${ppm(m.tdsPpm)}")
                Text(if (m.isWarmup) "안정화 원본 · 통계 제외" else "통계 대상 원본", style = MaterialTheme.typography.bodySmall)
                Text(m.rawVoltageMv?.toPlainString()?.plus(" mV") ?: "전압 기록 없음", style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}
@Composable fun CommandScreen(s: MonitorState, vm: MonitorViewModel) {
    val c = s.command ?: return
    LazyColumn(Modifier.fillMaxSize().testTag("command-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GardenIntro("측정 요청 상태", "측정부터 보존까지, 차근차근 확인해요.", "측정 진행 일지")
            Text("${c.deviceId ?: "장치 확인 중"} · 약 60회 측정 후 결과를 전송합니다.")
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("명령  ${c.commandState}")
                Text("서버 보존  ${c.serverPreservation}")
                Text("장치 버퍼  ${c.deviceBuffer}")
                Text("관측 신선도  ${c.liveness}")
                c.deviceState?.let { Text("장치 상태  $it") }
                c.deviceFault?.let { Text("장치 오류  $it") }
                c.reason?.let { Text("사유  $it") }
            } }
        }
        item { Text("명령 접수·원본 저장·장치 버퍼 해제는 각각 다른 단계입니다.", style = MaterialTheme.typography.bodySmall) }
        item {
            OutlinedButton(onClick = vm::refreshCommand, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("상태 새로고침") }
            Button(onClick = { vm.session(c.sessionId) }, enabled = !s.busy && c.serverPreservation in listOf("NORMAL", "INCOMPLETE"), modifier = Modifier.fillMaxWidth()) { Text("보존 결과 보기") }
            OutlinedButton(onClick = vm::retryResult, enabled = !s.busy && s.pending == null && c.deviceBuffer == "HELD" && c.deviceState == "MANUAL_WAIT",
                modifier = Modifier.fillMaxWidth()) { Text("보관 중인 결과 다시 전송") }
        }
    }
}
