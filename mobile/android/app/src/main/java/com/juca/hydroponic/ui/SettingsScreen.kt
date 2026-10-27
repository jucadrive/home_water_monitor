package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import com.juca.hydroponic.BuildConfig
import com.juca.hydroponic.MonitorState

@Composable fun SettingsScreen(s: MonitorState, save: (String, String) -> Unit, checkDevice: () -> Unit,
    login: (String, String) -> Unit = { _, _ -> }, logout: () -> Unit = {}) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf<String?>(null) }
    var origin by rememberSaveable(s.origin) { mutableStateOf(s.origin) }
    var device by rememberSaveable(s.device) { mutableStateOf(s.device) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GardenIntro("정원과 연결하기", "기록을 보관할 서버와 측정 장치를 연결해요.", "정원 설정")
        Text("서버 연결", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(origin, { origin = it }, label = { Text("서버 주소") }, placeholder = { Text("https://서버주소:포트") },
            singleLine = true, enabled = !s.busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(device, { device = it }, label = { Text("장치 ID · 측정할 때 필요") }, singleLine = true,
            enabled = !s.busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = { save(origin.trim(), device.trim()) }, enabled = !s.busy && origin.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("저장하고 연결") }
        if (s.origin.isNotEmpty()) {
            if (s.authenticated) {
                Text("개인 계정으로 로그인됨")
                OutlinedButton(onClick = logout, enabled = !s.busy) { Text("로그아웃") }
                Text("로그아웃은 진행 중인 서버 작업을 취소하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            } else OwnerLogin(s.busy, login)
        }
        if (s.authenticated && s.device.isNotEmpty()) OutlinedButton(onClick = checkDevice, enabled = !s.busy) { Text("저장된 장치 상태 확인") }
        s.deviceStatus?.let { Text(it) }
        TextButton(onClick = {
            licenses = listOf("gowun_dodum", "gamja_flower").joinToString("\n\n") { name ->
                context.assets.open("licenses/$name.txt").bufferedReader().use { it.readText() }
            }
        }) { Text("사용한 글꼴과 라이선스") }
        if (BuildConfig.DEBUG) Text("개발 빌드: HTTP는 localhost·127.0.0.1·10.0.2.2에만 연결할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    }
    licenses?.let { text -> AlertDialog(onDismissRequest = { licenses = null },
        title = { Text("고운돋움 · 감자꽃") },
        text = { SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { licenses = null }) { Text("닫기") } }) }
}
