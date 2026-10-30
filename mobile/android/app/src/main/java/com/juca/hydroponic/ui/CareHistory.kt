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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CareHistorySheet(sessions:List<SessionSummary>,more:Boolean,busy:Boolean,
    loadMore:()->Unit,select:(SessionSummary)->Unit,dismiss:()->Unit,error:String?=null) {
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=Cream) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=640.dp).testTag("history-list"),contentPadding=PaddingValues(CareSpace.Screen),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("측정 이력",style=MaterialTheme.typography.titleLarge) }
            if(error!=null) item { Text(error,color=MaterialTheme.colorScheme.error) }
            if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(sessions.isEmpty()) item { GardenEmpty("아직 측정 기록이 없어요","새 측정이 보존되면 이곳에서 확인할 수 있어요.") }
            items(sessions,key={it.sessionId}) { SessionCard(it,!busy){select(it)} }
            if(more) item { OutlinedButton(onClick=loadMore,enabled=!busy){Text("측정 이력 더 보기")} }
            item { TextButton(onClick=dismiss){Text("닫기")} }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CareEventSheet(event:CareEvent,cultivation:Cultivation,dismiss:()->Unit) {
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
            TextButton(onClick=dismiss){Text("닫기")}
            Spacer(Modifier.height(18.dp))
        }
    }
}
