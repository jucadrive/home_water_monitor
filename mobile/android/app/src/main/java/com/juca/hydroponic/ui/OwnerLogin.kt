package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable fun OwnerLogin(busy: Boolean, login: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    // Password intentionally never enters rememberSaveable, preferences, ViewModel state, or logs.
    var password by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(CareSpace.Card),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("내 계정 로그인", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(username, { username = it }, label = { Text("계정 이름") }, singleLine = true,
            enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = { val submitted = password; password = ""; login(username.trim(), submitted) },
            enabled = !busy && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("로그인") }
        Text("본인용 계정 하나로 사용합니다. 인증이 만료되면 다시 로그인하세요.", style = MaterialTheme.typography.bodySmall)
    }
    }
}
