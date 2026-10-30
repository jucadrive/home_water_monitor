package com.juca.hydroponic.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.juca.hydroponic.R

object AquaPalette {
    val Background=Color(0xFFF8FAF6)
    val Primary=Color(0xFF286B56)
    val Text=Color(0xFF193F35)
    val Mint=Color(0xFFD7EEE6)
    val Aqua=Color(0xFFB9E7E0)
    val White=Color(0xFFFFFFFF)
    val Muted=Color(0xFF4C6259)
    val Outline=Color(0xFFD8E3DB)
    val Soft=Color(0xFFEEF4EF)
    val Amber=Color(0xFF76551F)
    val AmberSurface=Color(0xFFF8EED8)
    val Error=Color(0xFF9B3730)
    val ErrorSurface=Color(0xFFFCEDE8)
}
object CareSpace {
    val Screen=22.dp
    val Card=22.dp
    val Gap=16.dp
    val Radius=24.dp
}
val Forest=AquaPalette.Primary
val Leaf=AquaPalette.Mint
val Cream=AquaPalette.Background
val Muted=AquaPalette.Muted
val BrandFont=FontFamily(Font(R.font.gamja_flower))
val NumberStyle=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,
    fontSize=42.sp,lineHeight=50.sp,letterSpacing=(-1).sp,fontFeatureSettings="tnum")
private fun body(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(
    fontFamily=FontFamily.SansSerif,fontWeight=weight,fontSize=size.sp,lineHeight=(size+7).sp)
@Composable fun GardenTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(
        primary=Forest,onPrimary=AquaPalette.White,primaryContainer=Leaf,onPrimaryContainer=AquaPalette.Text,
        secondary=Forest,onSecondary=AquaPalette.White,secondaryContainer=AquaPalette.Aqua,onSecondaryContainer=AquaPalette.Text,
        tertiary=AquaPalette.Amber,tertiaryContainer=AquaPalette.AmberSurface,onTertiaryContainer=AquaPalette.Amber,
        background=Cream,onBackground=AquaPalette.Text,surface=AquaPalette.White,onSurface=AquaPalette.Text,
        surfaceVariant=AquaPalette.Soft,onSurfaceVariant=Muted,
        surfaceContainerLowest=AquaPalette.White,surfaceContainerLow=AquaPalette.White,
        surfaceContainer=AquaPalette.White,surfaceContainerHigh=AquaPalette.Soft,surfaceContainerHighest=AquaPalette.White,
        outline=Muted,outlineVariant=AquaPalette.Outline,
        error=AquaPalette.Error,onError=AquaPalette.White,errorContainer=AquaPalette.ErrorSurface,onErrorContainer=AquaPalette.Error
    ),typography=Typography(
        displayLarge=NumberStyle,displayMedium=NumberStyle,displaySmall=NumberStyle,
        headlineLarge=body(30,FontWeight.Bold),headlineMedium=body(26,FontWeight.Bold),headlineSmall=body(23,FontWeight.Bold),
        titleLarge=body(21,FontWeight.SemiBold),titleMedium=body(17,FontWeight.SemiBold),titleSmall=body(15,FontWeight.SemiBold),
        bodyLarge=body(16),bodyMedium=body(14),bodySmall=body(12),
        labelLarge=body(14,FontWeight.SemiBold),labelMedium=body(12,FontWeight.Medium),labelSmall=body(11)
    ),shapes=Shapes(extraSmall=RoundedCornerShape(12.dp),small=RoundedCornerShape(16.dp),
        medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(CareSpace.Radius),extraLarge=RoundedCornerShape(28.dp)),
        content={CompositionLocalProvider(LocalContentColor provides AquaPalette.Text){content()}})
}
