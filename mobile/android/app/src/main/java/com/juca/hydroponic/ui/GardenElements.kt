package com.juca.hydroponic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Decorative plant/glass artwork, never a water-level or plant-health visualization.
@Composable fun Sprout(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val u=size.minDimension/100f
        drawRoundRect(AquaPalette.Mint,cornerRadius=CornerRadius(25*u))
        val vase=Path().apply { moveTo(29*u,49*u);lineTo(71*u,49*u);lineTo(67*u,84*u);quadraticTo(50*u,91*u,33*u,84*u);close() }
        drawPath(vase,AquaPalette.White.copy(alpha=.75f));drawPath(vase,Forest.copy(alpha=.14f),style=Stroke(1.3f*u))
        drawLine(Forest,Offset(50*u,72*u),Offset(50*u,34*u),2.5f*u,StrokeCap.Round)
        val leaf=Path().apply { moveTo(50*u,43*u);cubicTo(21*u,45*u,25*u,20*u,27*u,20*u);cubicTo(44*u,20*u,52*u,29*u,50*u,43*u) }
        drawPath(leaf,Forest)
        val right=Path().apply { moveTo(50*u,35*u);cubicTo(48*u,14*u,66*u,10*u,75*u,16*u);cubicTo(75*u,31*u,62*u,39*u,50*u,35*u) }
        drawPath(right,Forest.copy(alpha=.65f))
        drawLine(AquaPalette.White,Offset(38*u,59*u),Offset(37*u,77*u),2*u,StrokeCap.Round)
    }
}
@Composable fun GardenBadge(text:String,warm:Boolean=false) {
    Surface(color=if(warm) AquaPalette.AmberSurface else Leaf,
        contentColor=if(warm) AquaPalette.Amber else Forest,shape=RoundedCornerShape(50)) {
        Text(text,Modifier.padding(horizontal=11.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)
    }
}
@Composable fun GardenIntro(title:String,subtitle:String,eyebrow:String="뽀싹 돌봄 일지") {
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(eyebrow,color=Forest,style=MaterialTheme.typography.labelMedium)
        Text(title,style=MaterialTheme.typography.headlineLarge)
        Text(subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun GardenEmpty(title:String,description:String) {
    Column(Modifier.fillMaxWidth().background(AquaPalette.Soft,RoundedCornerShape(CareSpace.Radius)).padding(26.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Sprout(Modifier.size(58.dp))
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,color=Muted,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun GardenSection(title:String,note:String?=null) {
    Column(Modifier.padding(top=10.dp,bottom=2.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge)
        note?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodySmall) }
    }
}
