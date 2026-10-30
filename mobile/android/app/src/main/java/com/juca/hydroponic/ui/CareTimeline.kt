package com.juca.hydroponic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.data.*
import java.time.*
import java.time.format.DateTimeFormatter

data class CareEvent(val key:String,val time:String,val title:String,val detail:String,val kind:String,
    val session:SessionSummary?=null,val maintenance:Maintenance?=null)
fun careEvents(c:Cultivation,sessions:List<SessionSummary>,maintenance:List<Maintenance>):List<CareEvent> {
    val values=maintenance.map { CareEvent("m-${it.id}",it.occurredAt,maintenanceLabel(it),
        it.amountMl?.stripTrailingZeros()?.toPlainString()?.plus(" mL")?:"작업량 미상",if(it.type=="NUTRIENT_ADD") "add" else "water",maintenance=it) }+
        sessions.map { CareEvent("s-${it.sessionId}",it.displayAt,"TDS 측정",
            (if(it.preservationResult=="NORMAL") ppm(it.medianTdsPpm) else "불완전 보존 · 통계 없음")+" · "+basis(it.timeBasis),"measure",session=it) }+
        listOf(CareEvent("start-${c.id}",c.startedAt,"재배 시작",c.plantName,"plant"))+
        listOfNotNull(c.endedAt?.let { CareEvent("end-${c.id}",it,"재배 종료",c.name,"plant") })
    return values.sortedWith(compareByDescending<CareEvent> { Instant.parse(it.time) }.thenBy { it.key })
}
fun careDay(time:String,zone:ZoneId=ZoneId.systemDefault())=Instant.parse(time).atZone(zone).toLocalDate()
fun careDayLabel(day:LocalDate,today:LocalDate=LocalDate.now()):String=when(day) {
    today->"오늘"
    today.minusDays(1)->"어제"
    else->day.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
}
@Composable fun CareSymbol(kind:String,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val u=size.minDimension/32f
        drawCircle(AquaPalette.Soft,size.minDimension/2)
        when(kind) {
            "measure" -> {
                val path=Path().apply { moveTo(8*u,22*u);lineTo(13*u,15*u);lineTo(18*u,18*u);lineTo(24*u,10*u) }
                drawPath(path,Forest,style=androidx.compose.ui.graphics.drawscope.Stroke(1.8f*u,cap=StrokeCap.Round))
            }
            "add" -> {
                drawLine(Forest,Offset(9*u,16*u),Offset(23*u,16*u),1.8f*u,StrokeCap.Round)
                drawLine(Forest,Offset(16*u,9*u),Offset(16*u,23*u),1.8f*u,StrokeCap.Round)
            }
            "plant" -> {
                drawOval(Forest,Offset(9*u,7*u),androidx.compose.ui.geometry.Size(15*u,13*u))
                drawLine(Forest,Offset(12*u,24*u),Offset(19*u,12*u),1.8f*u,StrokeCap.Round)
            }
            else -> {
                val path=Path().apply { moveTo(16*u,7*u);cubicTo(2*u,23*u,12*u,26*u,16*u,26*u);cubicTo(22*u,26*u,29*u,22*u,16*u,7*u) }
                drawPath(path,Forest.copy(alpha=.8f))
            }
        }
    }
}
@Composable fun CareTimelineRow(event:CareEvent,enabled:Boolean,click:()->Unit) {
    Card(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical=16.dp,horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            CareSymbol(event.kind,Modifier.size(34.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(event.title,style=MaterialTheme.typography.titleSmall)
                Text(event.detail,color=Muted,style=MaterialTheme.typography.bodySmall)
                Text(Instant.parse(event.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),style=MaterialTheme.typography.labelMedium,color=Forest)
            }
        }
    }
}
