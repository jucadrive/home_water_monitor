package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.juca.hydroponic.data.*

@Composable fun CareHistorySheet(sessions:List<SessionSummary>,more:Boolean,busy:Boolean,
    loadMore:()->Unit,select:(SessionSummary)->Unit,dismiss:()->Unit,error:String?=null,offset:Int=0,previous:()->Unit={}) {
    StableHistorySheet("측정 이력",dismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("history-list"),contentPadding=PaddingValues(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(error!=null) item { Text(error,color=MaterialTheme.colorScheme.error) }
            if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(sessions.isEmpty()&&!busy&&error==null) item { GardenEmpty("아직 측정 기록이 없어요","새 측정이 보존되면 이곳에서 확인할 수 있어요.") }
            items(sessions,key={it.sessionId}) { SessionCard(it,!busy){select(it)} }
            item { HistoryNavigation(offset,more,busy,previous,loadMore) }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CareEventSheet(event:CareEvent,cultivation:Cultivation,dismiss:()->Unit,edit:(()->Unit)?=null,enabled:Boolean=true) {
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(event.title,style=MaterialTheme.typography.titleLarge)
            Text(date(event.time),color=Muted)
            Text(event.detail)
            event.maintenance?.memo?.takeIf { it.isNotBlank() }?.let { Text(it) }
            if(event.kind=="plant") {
                Text(cultivation.name)
                cultivation.memo.takeIf { it.isNotBlank() }?.let { Text(it) }
            }
            if(event.maintenance!=null && edit!=null) OutlinedButton(onClick=edit,enabled=enabled){Text("이 기록 수정")}
            TextButton(onClick=dismiss){Text("닫기")}
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable private fun HistoryNavigation(offset:Int,more:Boolean,busy:Boolean,previous:()->Unit,next:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("${offset/50+1}페이지 · 최대 50개씩 표시",style=MaterialTheme.typography.bodySmall,color=Muted)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick=previous,enabled=!busy&&offset>0){Text("이전 50개")}
            OutlinedButton(onClick=next,enabled=!busy&&more){Text("다음 50개")}
        }
    }
}

@Composable fun CareRecordsSheet(rows:List<Maintenance>,offset:Int,more:Boolean,busy:Boolean,error:String?,
    previous:()->Unit,next:()->Unit,select:(Maintenance)->Unit,dismiss:()->Unit) {
    StableHistorySheet("돌봄 기록",dismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("care-history-list"),contentPadding=PaddingValues(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(error!=null)item { Text(error,color=MaterialTheme.colorScheme.error) }
            if(busy)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(rows.isEmpty()&&!busy&&error==null)item { Text("이 페이지에는 돌봄 기록이 없어요.") }
            items(rows,key={it.id}) { row->
                Card(onClick={select(row)},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(maintenanceLabel(row),style=MaterialTheme.typography.titleSmall)
                        Text(date(row.occurredAt),color=Muted)
                        Text(row.amountMl?.stripTrailingZeros()?.toPlainString()?.plus(" mL")?:"작업량 미상")
                    }
                }
            }
            item { HistoryNavigation(offset,more,busy,previous,next) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StableHistorySheet(title:String,dismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    // Reading a list must not also drag/resize its parent sheet. Keep one vertical gesture owner.
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=Cream,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),sheetGesturesEnabled=false,dragHandle=null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)) {
            Row(Modifier.fillMaxWidth().padding(start=CareSpace.Screen,end=12.dp,top=8.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                TextButton(onClick=dismiss){Text("닫기")}
            }
            content()
        }
    }
}
