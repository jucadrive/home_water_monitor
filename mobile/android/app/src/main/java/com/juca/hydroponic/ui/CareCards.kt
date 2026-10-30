package com.juca.hydroponic.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.math.BigDecimal

@Composable fun AquaMetricCard(content:@Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(CareSpace.Radius))
        .background(Brush.linearGradient(listOf(AquaPalette.Mint,AquaPalette.Aqua,AquaPalette.White)))
        .border(1.dp,AquaPalette.White.copy(alpha=.85f),RoundedCornerShape(CareSpace.Radius))) {
        Canvas(Modifier.matchParentSize()) {
            // Fixed decorative curve: its height does not depend on sensor data.
            val water=Path().apply { moveTo(0f,size.height*.76f);cubicTo(size.width*.32f,size.height*.55f,
                size.width*.65f,size.height*.96f,size.width,size.height*.65f);lineTo(size.width,size.height);lineTo(0f,size.height);close() }
            drawPath(water,AquaPalette.White.copy(alpha=.24f))
            drawLine(AquaPalette.White.copy(alpha=.65f),Offset(22.dp.toPx(),22.dp.toPx()),
                Offset(size.width*.35f,22.dp.toPx()),2.dp.toPx(),StrokeCap.Round)
        }
        Column(Modifier.fillMaxWidth().padding(CareSpace.Card),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun MetricValue(value:BigDecimal?,label:String="TDS 중앙값") {
    Text(label,color=Muted,style=MaterialTheme.typography.labelLarge)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale=LocalDensity.current.fontScale
        val text=value?.setScale(2,java.math.RoundingMode.HALF_UP)?.toPlainString()
        val fit=if(text==null) 24f else (maxWidth.value/((text.length+4)*.62f*scale)).coerceIn(24f,42f)
        Text(if(text==null) "측정 기록 없음" else "$text ppm",modifier=Modifier.testTag("metric-value"),style=NumberStyle.copy(fontSize=fit.sp,lineHeight=(fit+8).sp))
    }
}
@Composable fun CareFact(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,color=Muted,style=MaterialTheme.typography.labelMedium)
        Text(value,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable fun CareStat(label:String,value:String,modifier:Modifier=Modifier) {
    Surface(modifier,shape=RoundedCornerShape(20.dp),color=AquaPalette.White,
        border=BorderStroke(1.dp,AquaPalette.Outline)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(label,color=Muted,style=MaterialTheme.typography.labelMedium)
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
    }
}
@Composable fun CareLoading() {
    Column(Modifier.fillMaxSize().padding(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("기록을 불러오고 있어요",style=MaterialTheme.typography.titleMedium)
        for(height in listOf(30,172,92,92)) Box(Modifier.fillMaxWidth().height(height.dp)
            .background(AquaPalette.Mint.copy(alpha=.55f),RoundedCornerShape(22.dp)))
    }
}
@Composable fun CareStats(values:List<Pair<String,String>>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val single=maxWidth<300.dp||LocalDensity.current.fontScale>1.3f
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(single) values.forEach { CareStat(it.first,it.second,Modifier.fillMaxWidth()) }
            else values.chunked(2).forEach { pair->
                Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    pair.forEach { CareStat(it.first,it.second,Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}
