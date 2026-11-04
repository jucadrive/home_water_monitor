package com.juca.hydroponic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.data.Cultivation

@Composable fun CultivationHeader(c:Cultivation,days:Long,enabled:Boolean,edit:()->Unit,end:()->Unit) {
    var menu by rememberSaveable(c.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(c.id,c.memo) { mutableStateOf(false) }
    var overflow by remember(c.id,c.memo) { mutableStateOf(false) }
    val active=c.status=="ACTIVE"
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(c.name,style=MaterialTheme.typography.headlineMedium)
                Text("${c.plantName} · ${if(active) "재배 중 · ${days}일째" else "종료 · ${days}일간 함께한 기록"}",
                    color=Forest,style=MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick={menu=true},enabled=enabled,modifier=Modifier.size(48.dp).semantics { contentDescription="재배 메뉴" }) {
                    Canvas(Modifier.size(20.dp)) {
                        listOf(.2f,.5f,.8f).forEach { y->drawCircle(Forest,1.7.dp.toPx(),Offset(size.width/2,size.height*y)) }
                    }
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("재배 정보 편집")},enabled=enabled,onClick={menu=false;edit()})
                    if(active) DropdownMenuItem(text={Text("재배 종료",color=MaterialTheme.colorScheme.error)},
                        enabled=enabled,onClick={menu=false;end()})
                }
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("시작 ${date(c.startedAt)}",color=Muted,style=MaterialTheme.typography.bodySmall)
            c.endedAt?.let { Text("종료 ${date(it)}",color=Muted,style=MaterialTheme.typography.bodySmall) }
        }
        if(c.memo.isNotBlank()) {
            Text(c.memo,style=MaterialTheme.typography.bodyMedium,maxLines=if(expanded)Int.MAX_VALUE else 3,
                overflow=TextOverflow.Ellipsis,onTextLayout={if(!expanded)overflow=it.hasVisualOverflow})
            if(overflow||expanded) TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(horizontal=0.dp)) {
                Text(if(expanded)"메모 접기" else "메모 더 보기")
            }
        }
    }
}
