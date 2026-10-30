package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun date(value:String?):String=value?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")) }?:"기록 없음"
fun ppm(value:BigDecimal?):String=value?.setScale(2,java.math.RoundingMode.HALF_UP)?.toPlainString()?.plus(" ppm")?:"측정 기록 없음"

@Composable fun DashboardScreen(s:MonitorState,vm:MonitorViewModel) {
    val recent=s.cultivations.mapNotNull { it.latestMeasurement }.maxByOrNull { Instant.parse(it.displayAt) }
    LazyColumn(Modifier.fillMaxSize().testTag("dashboard-list"),contentPadding=PaddingValues(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { GardenIntro("오늘도, 초록 한 스푼","식물의 오늘을 천천히 살펴보세요.") }
        item {
            AquaMetricCard {
                Text("오늘의 재배 기록",style=MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CareFact(if(s.moreCultivations) "불러온 목록 · 재배 중" else "재배 중인 식물","${s.cultivations.count { it.cultivation.status=="ACTIVE" }}개")
                    CareFact("최근 실측 기록",if(recent==null) "아직 없어요" else "저장된 기록 있음")
                }
                Text("최근 측정  ${date(recent?.displayAt)}",style=MaterialTheme.typography.bodySmall,color=Muted)
                if(s.moreCultivations) Text("현재 불러온 재배 ${s.cultivations.size}개 기준",style=MaterialTheme.typography.bodySmall)
            }
        }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Button(onClick=vm::createForm,enabled=!s.busy&&s.pending==null,contentPadding=PaddingValues(horizontal=24.dp,vertical=14.dp)) { Text("재배 등록") }
                OutlinedButton(onClick={vm.list()},enabled=!s.busy) { Text("새로고침") }
            }
        }
        item { GardenSection("나의 초록 친구들","이름을 누르면 자세한 기록을 볼 수 있어요") }
        if(s.cultivations.isEmpty()) item {
            if(s.error!=null) GardenEmpty("목록을 확인하지 못했어요","연결 상태를 확인한 뒤 다시 불러와 주세요.")
            else GardenEmpty("첫 초록 친구를 기다려요","재배를 등록하고 식물과 물의 변화를 기록해 보세요.")
        }
        items(s.cultivations,key={it.cultivation.id}) { overview->
            val c=overview.cultivation
            Card(onClick={vm.detail(c.id)},enabled=!s.busy,modifier=Modifier.fillMaxWidth(),
                border=androidx.compose.foundation.BorderStroke(1.dp,AquaPalette.Outline)) {
                Column(Modifier.padding(CareSpace.Card),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Sprout(Modifier.size(52.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(c.name,style=MaterialTheme.typography.titleLarge)
                            Text(c.plantName,color=Muted,style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    GardenBadge(if(c.status=="ACTIVE") "재배 중" else "종료한 재배",c.status!="ACTIVE")
                    HorizontalDivider(color=AquaPalette.Outline)
                    Text("최근 실측 중앙값",style=MaterialTheme.typography.labelMedium,color=Muted)
                    Text(ppm(overview.latestMeasurement?.medianTdsPpm),style=MaterialTheme.typography.headlineMedium)
                    Text("${date(overview.latestMeasurement?.displayAt)} · ${overview.latestMeasurement?.let { basis(it.timeBasis) }?:"측정 전"}",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
            }
        }
        if(s.moreCultivations) item { OutlinedButton(onClick={vm.list(true)},enabled=!s.busy) { Text("재배 더 보기") } }
        item { TextButton(onClick={vm.unlinked()},enabled=!s.busy) { Text("재배에 연결되지 않은 측정 기록") } }
        if(s.command!=null) item { TextButton(onClick=vm::commandScreen,enabled=!s.busy) { Text("최근 측정 요청 상태") } }
        item { Text("TDS는 농도 변화의 참고 기록입니다. 식물 건강이나 교체 필요성을 확정하지 않습니다.",style=MaterialTheme.typography.bodySmall,color=Muted) }
    }
}
