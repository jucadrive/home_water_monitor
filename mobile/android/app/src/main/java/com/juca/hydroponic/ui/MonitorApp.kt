package com.juca.hydroponic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.juca.hydroponic.*
import com.juca.hydroponic.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MonitorApp(vm: MonitorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var discard by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val command = state.command
    LaunchedEffect(state.screen, command?.commandId, command?.commandState, command?.deviceBuffer) {
        if (state.screen == Screen.COMMAND && command != null && command.deviceBuffer != "RELEASE_CONFIRMED" &&
            command.commandState !in listOf("REJECTED", "NOT_DISPATCHED", "NOT_EXECUTED", "INTERRUPTED_REBOOT")) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { delay(3000); vm.refreshCommand() } }
        }
    }
    BackHandler(state.busy || state.authenticated && state.screen != Screen.LIST) { vm.back() }
    GardenTheme {
        Scaffold(topBar = {
            TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=Cream),title = { Text(stringResource(R.string.app_name), fontFamily=BrandFont,fontSize=32.sp,color=Forest) }, navigationIcon = {
                if (state.authenticated && state.screen != Screen.LIST) TextButton(onClick = vm::back, enabled = !state.busy) { Text("뒤로") }
            }, actions = { if (state.screen != Screen.SETTINGS) TextButton(onClick = vm::settings, enabled = !state.busy) { Text("설정") } })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy&&!state.initialLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("불러오거나 처리 중이에요",Modifier.padding(horizontal=CareSpace.Screen,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
                }
                state.error?.let { error ->
                    Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("잠깐, 연결을 살펴볼까요?",style=MaterialTheme.typography.titleMedium)
                            Text(error,style=MaterialTheme.typography.bodyMedium)
                            FlowRow {
                                if(state.readRetryAvailable) TextButton(onClick=vm::retryRead,enabled=!state.busy){Text("다시 불러오기")}
                                TextButton(onClick=vm::dismissError){Text("확인")}
                            }
                        }
                    }
                }
                if (state.pending != null) Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("처리 결과를 확인할 요청이 있습니다.")
                        Text("새 요청을 만들지 않고 같은 키와 내용으로 재시도합니다.", style = MaterialTheme.typography.bodySmall)
                        FlowRow {
                            TextButton(onClick = vm::retryPending, enabled = !state.busy && state.authenticated) { Text("같은 요청 재시도") }
                            TextButton(onClick = { discard = true }, enabled = !state.busy) { Text("추적 중단") }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    if(state.initialLoading) CareLoading()
                    else AnimatedContent(targetState=state.screen,transitionSpec={fadeIn(tween(160)) togetherWith fadeOut(tween(90))},label="care-screen") { screen ->
                    when (screen) {
                        Screen.SETTINGS -> SettingsScreen(state, vm::configure, vm::checkDevice, vm::login, vm::logout)
                        Screen.LIST -> DashboardScreen(state, vm)
                        Screen.CREATE -> CultivationForm(state.busy || state.pending != null, vm::create)
                        Screen.DETAIL -> if(state.detail==null) GardenEmpty("기록을 불러오지 못했어요","연결 상태를 확인하고 다시 불러와 주세요.") else DetailScreen(state, vm)
                        Screen.MAINTENANCE -> MaintenanceForm(state.busy || state.pending != null, vm::maintenance)
                        Screen.UNLINKED -> UnlinkedScreen(state, vm)
                        Screen.SESSION -> SessionScreen(state.session)
                        Screen.COMMAND -> CommandScreen(state, vm)
                    }
                    }
                }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("요청 추적을 중단할까요?") },
            text = { Text("서버에서는 이미 처리됐을 수 있습니다. 기록과 장치 상태를 확인한 뒤 새 요청을 만드세요.") },
            confirmButton = { TextButton(onClick = { vm.abandonPending(); discard = false }) { Text("추적 중단") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("계속 확인") } })
    }
}
