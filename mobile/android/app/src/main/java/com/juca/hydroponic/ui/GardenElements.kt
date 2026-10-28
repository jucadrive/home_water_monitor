package com.juca.hydroponic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Decorative vector artwork. Its expression never represents a measured plant-health assessment.
@Composable fun Sprout(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 120f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        drawCircle(Color(0xFFE4EED4), 54 * unit, p(60f, 60f))
        drawCircle(Color(0xFFF1D884), 9 * unit, p(99f, 21f))
        drawOval(Color(0xFFCEDBBE), p(22f, 105f), Size(76 * unit, 8 * unit))
        drawLine(Forest, p(60f, 76f), p(60f, 40f), 4 * unit, StrokeCap.Round)
        val left = Path().apply {
            moveTo(60 * unit, 53 * unit); cubicTo(30 * unit, 57 * unit, 24 * unit, 24 * unit, 29 * unit, 22 * unit)
            cubicTo(49 * unit, 19 * unit, 63 * unit, 32 * unit, 60 * unit, 53 * unit); close()
        }
        drawPath(left, Color(0xFF79A65B))
        val right = Path().apply {
            moveTo(60 * unit, 44 * unit); cubicTo(58 * unit, 20 * unit, 77 * unit, 12 * unit, 91 * unit, 18 * unit)
            cubicTo(94 * unit, 35 * unit, 82 * unit, 48 * unit, 60 * unit, 44 * unit); close()
        }
        drawPath(right, Color(0xFF3C7952))
        drawLine(Color(0xFFC7DEA7), p(57f, 48f), p(37f, 31f), 2 * unit, StrokeCap.Round)
        drawLine(Color(0xFF9EC38B), p(64f, 38f), p(82f, 24f), 2 * unit, StrokeCap.Round)
        val pot = Path().apply {
            moveTo(34 * unit, 70 * unit); lineTo(86 * unit, 70 * unit); lineTo(80 * unit, 101 * unit)
            quadraticTo(60 * unit, 110 * unit, 40 * unit, 101 * unit); close()
        }
        drawPath(pot, Color(0xFFE6B58A))
        drawRoundRect(Color(0xFFF2CEA6), p(30f, 65f), Size(60 * unit, 12 * unit), CornerRadius(5 * unit))
        drawCircle(Forest, 2 * unit, p(51f, 88f)); drawCircle(Forest, 2 * unit, p(69f, 88f))
        drawCircle(Color(0xFFD98D80), 4 * unit, p(44f, 93f)); drawCircle(Color(0xFFD98D80), 4 * unit, p(76f, 93f))
        drawArc(Forest, 10f, 160f, false, p(55f, 89f), Size(10 * unit, 7 * unit), style = Stroke(1.8f * unit, cap = StrokeCap.Round))
    }
}

@Composable fun GardenBadge(text: String, warm: Boolean = false) {
    Surface(color = if (warm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (warm) MaterialTheme.colorScheme.onTertiaryContainer else Forest, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun GardenIntro(title: String, subtitle: String, eyebrow: String = "뽀싹 돌봄 일지") {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, color = Muted, style = MaterialTheme.typography.labelMedium)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun GardenEmpty(title: String, description: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFEDF2E3), RoundedCornerShape(26.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sprout(Modifier.size(106.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun GardenSection(title: String, note: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        note?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}
