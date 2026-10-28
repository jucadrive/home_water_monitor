package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.MonitorState

@Composable fun SettingsScreen(s: MonitorState, save: (String, String) -> Unit, checkDevice: () -> Unit,
    login: (String, String) -> Unit = { _, _ -> }, logout: () -> Unit = {}) {
    var device by rememberSaveable(s.device) { mutableStateOf(s.device) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!s.authenticated) {
            GardenIntro("나의 돌봄을 이어가요", "로그인하고 식물의 기록을 만나보세요.")
            if (s.pending != null) Text("확인 중인 요청은 원래 서버에 보존되어 있습니다. 해당 서버의 계정으로 로그인하세요.",
                style = MaterialTheme.typography.bodySmall)
            OwnerLogin(s.busy, login)
        } else {
            GardenIntro("돌봄 설정", "측정 장치와 내 계정을 관리해요.")
            Text("측정 장치", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(device, { device = it }, label = { Text("장치 ID · 측정할 때 필요") }, singleLine = true,
                enabled = !s.busy, modifier = Modifier.fillMaxWidth())
            Button(onClick = { save(s.origin, device.trim()) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("장치 설정 저장") }
            if (s.device.isNotEmpty()) OutlinedButton(onClick = checkDevice, enabled = !s.busy) { Text("저장된 장치 상태 확인") }
            s.deviceStatus?.let { Text(it) }
            HorizontalDivider()
            Text("내 계정", style = MaterialTheme.typography.titleLarge)
            Text("개인 계정으로 로그인됨")
            OutlinedButton(onClick = logout, enabled = !s.busy) { Text("로그아웃") }
            Text("로그아웃은 진행 중인 서버 작업을 취소하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
