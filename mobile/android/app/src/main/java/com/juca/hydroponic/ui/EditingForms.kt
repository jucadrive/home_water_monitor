package com.juca.hydroponic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.juca.hydroponic.*
import com.juca.hydroponic.data.*

@Composable fun ProfileEditForm(c:Cultivation,disabled:Boolean,conflict:Boolean,
    latest:Cultivation?,reload:()->Unit,accept:()->Unit,cancel:()->Unit,submit:(String,String,String)->Unit) {
    // Key by identity, not version: an explicit conflict refresh must preserve the user's draft.
    var plant by rememberSaveable(c.id) { mutableStateOf(c.plantName) }
    var name by rememberSaveable(c.id) { mutableStateOf(c.name) }
    var memo by rememberSaveable(c.id) { mutableStateOf(c.memo) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CareSpace.Screen).testTag("profile-edit"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        GardenIntro("재배 정보 편집","같은 식물의 이름과 메모를 고쳐요. 측정 이력은 유지됩니다.")
        EditConflict(conflict,disabled,latest?.let { "식물명: ${it.plantName}\n재배 별명: ${it.name}\n메모: ${it.memo.ifBlank { "없음" }}" },reload,accept)
        OutlinedTextField(plant,{plant=it},label={Text("식물명")},singleLine=true,enabled=!disabled,modifier=Modifier.fillMaxWidth(),isError=plant.isBlank()||plant.length>100)
        OutlinedTextField(name,{name=it},label={Text("재배 별명")},singleLine=true,enabled=!disabled,modifier=Modifier.fillMaxWidth(),isError=name.isBlank()||name.length>100)
        MemoField(memo,{memo=it},!disabled)
        Button(onClick={submit(plant,name,memo)},enabled=!disabled&&!conflict&&plant.isNotBlank()&&name.isNotBlank()&&plant.length<=100&&name.length<=100&&memo.length<=2000,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("변경 저장")}
        TextButton(onClick=cancel,enabled=!disabled){Text("취소")}
    }
}

@Composable fun MaintenanceEditForm(m:Maintenance,disabled:Boolean,conflict:Boolean,
    latest:Maintenance?,reload:()->Unit,accept:()->Unit,cancel:()->Unit,submit:(String,String?,String,String,String)->Unit) {
    var type by rememberSaveable(m.id) { mutableStateOf(m.type) }
    var scope by rememberSaveable(m.id) { mutableStateOf(m.replacementScope?:"FULL") }
    var time by rememberSaveable(m.id) { mutableStateOf(Dates.input(m.occurredAt)) }
    val originalAt=rememberSaveable(m.id) { m.occurredAt }
    var amount by rememberSaveable(m.id) { mutableStateOf(m.amountMl?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var memo by rememberSaveable(m.id) { mutableStateOf(m.memo) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CareSpace.Screen).testTag("maintenance-edit"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        GardenIntro("돌봄 기록 수정","잘못 적은 내용을 정정해요. 수정 전 기록은 서버에 보존됩니다.")
        EditConflict(conflict,disabled,latest?.let { "${maintenanceLabel(it)}\n${date(it.occurredAt)}\n작업량: ${it.amountMl?.toPlainString()?:"미상"} mL\n메모: ${it.memo.ifBlank { "없음" }}" },reload,accept)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("REPLACE" to "물 교체","TOP_UP" to "물 보충","NUTRIENT_ADD" to "영양액·영양제 추가").forEach { (value,label)->
                FilterChip(type==value,{type=value},{Text(label)},enabled=!disabled)
            }
        }
        if(type=="REPLACE") FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(scope=="FULL",{scope="FULL"},{Text("전체 교체")},enabled=!disabled)
            FilterChip(scope=="PARTIAL",{scope="PARTIAL"},{Text("부분 교체")},enabled=!disabled)
        }
        DateField(time,{time=it},!disabled,"작업 일시")
        OutlinedTextField(amount,{amount=it},label={Text("작업량 ml · 선택")},singleLine=true,enabled=!disabled,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        MemoField(memo,{memo=it},!disabled)
        Button(onClick={submit(type,scope.takeIf{type=="REPLACE"},if(time==Dates.input(originalAt))originalAt else time,amount,memo)},enabled=!disabled&&!conflict&&memo.length<=2000,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("변경 저장")}
        TextButton(onClick=cancel,enabled=!disabled){Text("취소")}
    }
}

@Composable private fun EditConflict(conflict:Boolean,disabled:Boolean,latest:String?,reload:()->Unit,accept:()->Unit) {
    if(conflict) Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("다른 곳에서 수정된 기록이에요",style=MaterialTheme.typography.titleSmall)
            Text("작성 중인 입력은 유지했어요. 서버의 최신 내용을 확인한 뒤 수정할 내용을 결정해 주세요.")
            if(latest!=null) {
                Text("서버의 최신 내용",style=MaterialTheme.typography.labelLarge)
                Text(latest)
                TextButton(onClick=accept,enabled=!disabled){Text("내 입력으로 수정 계속")}
            }
            TextButton(onClick=reload,enabled=!disabled){Text("최신 내용 확인")}
        }
    }
}
