package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.data.Dates

@Composable fun CultivationForm(disabled: Boolean, submit: (String, String, String, String) -> Unit) {
    var plant by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf(Dates.nowInput()) }
    var memo by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GardenIntro("새 식물 맞이하기", "함께 자랄 식물에게 이름을 지어주세요.")
        Text("독립적으로 관리하는 영양액 용기 하나의 재배를 기록합니다.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(plant, { plant = it }, label = { Text("식물명") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !disabled, isError = plant.length > 100)
        OutlinedTextField(name, { name = it }, label = { Text("재배 별명") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !disabled, isError = name.length > 100)
        DateField(time, { time = it }, !disabled, "재배 시작 일시")
        MemoField(memo, { memo = it }, !disabled)
        Button(onClick = { submit(plant, name, time, memo) }, modifier = Modifier.fillMaxWidth(),
            enabled = !disabled && plant.isNotBlank() && name.isNotBlank() && plant.length <= 100 && name.length <= 100 && memo.length <= 2000) { Text("재배 등록") }
    }
}
@Composable fun MaintenanceForm(disabled: Boolean, submit: (String, String?, String, String, String) -> Unit) {
    var type by rememberSaveable { mutableStateOf("REPLACE") }
    var scope by rememberSaveable { mutableStateOf("FULL") }
    var time by rememberSaveable { mutableStateOf(Dates.nowInput()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GardenIntro("물·영양액 관리 기록", "오늘 해 준 돌봄을 차곡차곡 남겨요.", "돌봄 노트")
        for ((value, label) in listOf("REPLACE" to "영양액 교체", "TOP_UP" to "물 보충", "NUTRIENT_ADD" to "영양액·영양제 추가")) {
            FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) }, enabled = !disabled)
        }
        if (type == "REPLACE") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(scope == "FULL", { scope = "FULL" }, { Text("전체 교체") }, enabled = !disabled)
            FilterChip(scope == "PARTIAL", { scope = "PARTIAL" }, { Text("부분 교체") }, enabled = !disabled)
        }
        DateField(time, { time = it }, !disabled, "작업 일시")
        OutlinedTextField(amount, { amount = it }, label = { Text("작업량 ml · 선택") }, modifier = Modifier.fillMaxWidth(),
            enabled = !disabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { Text("모르면 비워두세요. 영양제는 액상 부피를 기록합니다.") })
        MemoField(memo, { memo = it }, !disabled)
        Button(onClick = { submit(type, scope.takeIf { type == "REPLACE" }, time, amount, memo) }, modifier = Modifier.fillMaxWidth(),
            enabled = !disabled && memo.length <= 2000) { Text("관리 이력 등록") }
    }
}
@Composable private fun DateField(value: String, change: (String) -> Unit, enabled: Boolean, label: String) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        supportingText = { Text("기기 시간대 · YYYY-MM-DD HH:mm") })
}
@Composable private fun MemoField(value: String, change: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value, change, label = { Text("메모 · 선택") }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        minLines = 3, isError = value.length > 2000, supportingText = { Text("${value.length}/2000") })
}
