package com.juca.hydroponic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.juca.hydroponic.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable fun TrendChart(sessions:List<SessionSummary>,maintenance:List<Maintenance>,
    period:TrendPeriod=TrendPeriod.WEEK,end:Instant=Instant.now(),limited:Boolean=false,
    busy:Boolean=false,error:String?=null,enabled:Boolean=true,onPeriod:(TrendPeriod)->Unit={}) {
    val data=remember(sessions,period,end) { inTrendWindow(sessions,period,end) }
    var selectedId by rememberSaveable(period) { mutableStateOf<String?>(null) }
    var plotWidth by remember { mutableIntStateOf(1) }
    val color=Forest
    val from=period.start(end).toEpochMilli();val until=end.toEpochMilli()
    fun xFraction(time:Long)=((time-from).toDouble()/(until-from)).toFloat().coerceIn(0f,1f)
    Card(Modifier.fillMaxWidth(),border=androidx.compose.foundation.BorderStroke(1.dp,AquaPalette.Outline)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("TDS 변화",style=MaterialTheme.typography.titleLarge)
            Text("실측 정상 기록 · 세션별 중앙값 ppm",style=MaterialTheme.typography.bodySmall,color=Muted)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                TrendPeriod.entries.forEach { p->FilterChip(selected=period==p,onClick={selectedId=null;onPeriod(p)},
                    enabled=enabled&&!busy,label={Text(p.label)},modifier=Modifier.heightIn(min=48.dp)) }
            }
            when {
                busy -> { LinearProgressIndicator(Modifier.fillMaxWidth());Text("기간 기록을 불러오고 있어요",style=MaterialTheme.typography.bodySmall) }
                error!=null -> { Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={onPeriod(period)},enabled=enabled){Text("그래프 다시 불러오기")} }
                data.isEmpty() -> { Text("기록이 자라는 중이에요",style=MaterialTheme.typography.titleMedium)
                    Text("실측 정상 기록이 쌓이면 추세를 보여드립니다.",color=Muted)
                    Text("선택한 ${period.label} 동안의 기록이 없습니다.",style=MaterialTheme.typography.bodySmall,color=Muted) }
                else -> {
                    val selected=data.indexOfFirst { it.sessionId==selectedId }.takeIf { it>=0 }?:data.lastIndex
                    val observation=data[selected]
                    val scale=remember(data) { TrendScale.forValues(data.map { it.medianTdsPpm!!.toDouble() }) }
                    val high=scale.high;val low=scale.low
                    Text(when {
                        data.any { it.temperatureSource==null }&&data.any { it.temperatureSource=="DS18B20" } -> "수온 기준: 가정 수온 · 실측 수온 보정 기록"
                        data.any { it.temperatureSource=="DS18B20" } -> "수온 기준: 실측 수온 보정 기록"
                        else -> "수온 기준: 가정 수온 기록"
                    },style=MaterialTheme.typography.bodySmall,color=Muted)
                    Row(Modifier.fillMaxWidth().height(180.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.widthIn(min=38.dp).fillMaxHeight(),verticalArrangement=Arrangement.SpaceBetween) {
                            listOf(high,(low+high)/2,low).forEach { Text(it.toBigDecimal().stripTrailingZeros().toPlainString(),style=MaterialTheme.typography.labelSmall,color=Muted) }
                        }
                        Canvas(Modifier.weight(1f).fillMaxHeight().onSizeChanged { plotWidth=it.width }
                            .semantics { contentDescription="실측 정상 TDS 중앙값 추세, ${data.size}개 관측, 단위 ppm" }
                            .pointerInput(data,from,until,plotWidth) { detectTapGestures { point->
                                val fraction=(point.x/plotWidth).coerceIn(0f,1f)
                                selectedId=data.minByOrNull { abs(xFraction(Instant.parse(it.displayAt).toEpochMilli())-fraction) }?.sessionId
                            } }) {
                            val inset=6.dp.toPx()
                            fun x(t:Long)=inset+xFraction(t)*(size.width-2*inset)
                            fun y(v:Double)=inset+(1-(v-low)/(high-low)).toFloat()*(size.height-2*inset)
                            for(i in 0..4) drawLine(AquaPalette.Outline,Offset(0f,inset+i*(size.height-2*inset)/4),Offset(size.width,inset+i*(size.height-2*inset)/4),1.dp.toPx())
                            maintenance.filter { it.type=="REPLACE"&&it.replacementScope=="FULL" }.forEach {
                                val t=Instant.parse(it.occurredAt).toEpochMilli()
                                if(t in from..until) drawLine(AquaPalette.Amber.copy(alpha=.7f),Offset(x(t),0f),Offset(x(t),size.height),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,6f)))
                            }
                            val path=Path()
                            data.forEachIndexed { i,s->val point=Offset(x(Instant.parse(s.displayAt).toEpochMilli()),y(s.medianTdsPpm!!.toDouble()))
                                if(i==0)path.moveTo(point.x,point.y)else path.lineTo(point.x,point.y) }
                            drawPath(path,color,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                            val focus=Offset(x(Instant.parse(observation.displayAt).toEpochMilli()),y(observation.medianTdsPpm!!.toDouble()))
                            drawLine(color.copy(alpha=.2f),Offset(focus.x,0f),Offset(focus.x,size.height),1.dp.toPx())
                            drawCircle(AquaPalette.White,7.dp.toPx(),focus);drawCircle(color,4.dp.toPx(),focus)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(axisTime(period.start(end)),style=MaterialTheme.typography.labelSmall,color=Muted,modifier=Modifier.weight(1f))
                        Text(axisTime(end),style=MaterialTheme.typography.labelSmall,color=Muted)
                    }
                    if(data.size==1) Text("관측값이 1개여서 점으로 표시합니다.",style=MaterialTheme.typography.bodySmall,color=Muted)
                    Surface(color=AquaPalette.Soft,shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(ppm(observation.medianTdsPpm),style=MaterialTheme.typography.titleLarge)
                            Text("${date(observation.displayAt)} · ${basis(observation.timeBasis)}",style=MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={selectedId=data[selected-1].sessionId},enabled=selected>0,modifier=Modifier.heightIn(min=48.dp)){Text("이전 관측값")}
                                TextButton(onClick={selectedId=data[selected+1].sessionId},enabled=selected<data.lastIndex,modifier=Modifier.heightIn(min=48.dp)){Text("다음 관측값")}
                            }
                        }
                    }
                    Text("선은 관측값을 연결한 것입니다. 점선은 불러온 전체 물 교체 기록입니다.",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
            }
            if(limited) Text("조회 한도에 도달해 최근 최대 1,600건만 표시합니다. 기간 일부가 빠질 수 있어요.",style=MaterialTheme.typography.bodySmall,color=AquaPalette.Amber)
        }
    }
}
private fun axisTime(time:Instant)=time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
