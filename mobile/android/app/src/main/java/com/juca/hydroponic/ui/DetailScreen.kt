package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.*
import com.juca.hydroponic.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun maintenanceLabel(m:Maintenance):String=when(m.type) {
    "REPLACE"->if(m.replacementScope=="FULL") "전체 물 교체" else "부분 물 교체"
    "TOP_UP"->"물 보충"
    else->"영양액·영양제 추가"
}
fun basis(value:String):String=when(value){"MEASURED"->"측정 시각";"REQUESTED"->"측정 요청 시각";else->"서버 수신 시각"}
@Composable fun DetailScreen(s:MonitorState,vm:MonitorViewModel) {
    val data=s.detail?:return
    val c=data.cultivation
    var ending by rememberSaveable(c.id) { mutableStateOf(false) }
    var history by rememberSaveable(c.id) { mutableStateOf(false) }
    var event by remember { mutableStateOf<CareEvent?>(null) }
    val active=c.status=="ACTIVE"
    val until=c.endedAt?.let(Instant::parse)?:Instant.now()
    val days=ChronoUnit.DAYS.between(Instant.parse(c.startedAt).atZone(ZoneId.systemDefault()).toLocalDate(),until.atZone(ZoneId.systemDefault()).toLocalDate())+1
    val grouped=remember(c,s.sessions,s.maintenance){careEvents(c,s.sessions,s.maintenance).groupBy { careDay(it.time) }}
    LazyColumn(Modifier.fillMaxSize().testTag("detail-list"),contentPadding=PaddingValues(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            GardenBadge("${c.plantName} · ${if(active) "재배 중" else "종료"}",!active)
            GardenIntro(c.name,if(active) "함께 돌본 지 ${days}일째" else "${days}일간 함께한 기록","나의 식물 일지")
            Text("시작 ${date(c.startedAt)}",color=Muted,style=MaterialTheme.typography.bodySmall)
            c.endedAt?.let { Text("종료 ${date(it)}",color=Muted,style=MaterialTheme.typography.bodySmall) }
            if(c.memo.isNotBlank()) Text(c.memo,Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodyMedium)
        }
        item {
            AquaMetricCard {
                MetricValue(data.latestMeasurement?.medianTdsPpm,"최근 실측 중앙값")
                Text("${date(data.latestMeasurement?.displayAt)} · ${data.latestMeasurement?.let { basis(it.timeBasis) }?:"측정 전"}",style=MaterialTheme.typography.bodySmall,color=Muted)
                data.latestMeasurement?.let { Text(temperatureLabel(it),style=MaterialTheme.typography.bodyMedium) }
                Text("마지막 전체 교체  ${date(data.lastFullReplacementAt)}",style=MaterialTheme.typography.bodySmall,color=Muted)
                if(data.latestMeasurement!=null) TextButton(onClick={vm.session(data.latestMeasurement.sessionId)},enabled=!s.busy){Text("최근 측정 상세 보기")}
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=vm::start,enabled=active&&!s.busy&&s.pending==null&&s.device.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("${s.device.ifBlank { "장치 설정 필요" }} · 측정 시작") }
                OutlinedButton(onClick=vm::maintenanceForm,enabled=active&&!s.busy&&s.pending==null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("물·영양액 관리 기록") }
                TextButton(onClick={vm.detail(c.id)},enabled=!s.busy){Text("기록 새로고침")}
            }
        }
        item { TrendChart(s.trend,s.maintenance,s.trendPeriod,Instant.parse(s.trendEnd),s.trendLimited,s.chartBusy,s.chartError,!s.busy,vm::trendPeriod) }
        item {
            GardenSection("돌봄 타임라인","불러온 측정·관리 기록과 재배 이력을 함께 보여드려요")
            Text("데이터 보존 상태는 식물 건강 판정이 아닙니다.",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        grouped.forEach { (day,events)->
            item(key="day-$day"){Text(careDayLabel(day),style=MaterialTheme.typography.titleSmall,color=Forest)}
            items(events,key={it.key}){entry->CareTimelineRow(entry,!s.busy){
                if(entry.session!=null)vm.session(entry.session.sessionId)else event=entry
            }}
        }
        if(s.maintenance.isEmpty()) item { Text("아직 관리 기록이 없습니다.",style=MaterialTheme.typography.bodySmall,color=Muted) }
        if(s.moreMaintenance) item { TextButton(onClick={vm.moreHistory(true)},enabled=!s.busy){Text("관리 이력 더 보기")} }
        item { OutlinedButton(onClick={history=true},modifier=Modifier.fillMaxWidth(),enabled=!s.busy){Text("전체 측정 이력 보기")} }
        if(active) item { TextButton(onClick={ending=true},enabled=!s.busy&&s.pending==null){Text("재배 종료")} }
    }
    if(history) CareHistorySheet(s.sessions,s.moreSessions,s.busy,{vm.moreHistory(false)},{history=false;vm.session(it.sessionId)},{history=false},s.error)
    event?.let { CareEventSheet(it,c){event=null} }
    if(ending) AlertDialog(onDismissRequest={ending=false},title={Text("재배를 종료할까요?")},
        text={Text("새 측정·관리 등록을 막습니다. 이미 요청한 측정의 결과는 계속 보존합니다.")},
        confirmButton={TextButton(onClick={ending=false;vm.end()}){Text("종료")}},dismissButton={TextButton(onClick={ending=false}){Text("취소")}})
}
@Composable fun SessionCard(s:SessionSummary,enabled:Boolean,click:()->Unit) {
    Card(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CareSymbol("measure",Modifier.size(34.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(if(s.preservationResult=="NORMAL") ppm(s.medianTdsPpm) else "불완전 측정 · 통계 없음",style=MaterialTheme.typography.titleMedium)
                Text("${date(s.displayAt)} · ${basis(s.timeBasis)}",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("원본 ${s.preservedSampleCount}/60 · ${s.source}",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
    }
}
