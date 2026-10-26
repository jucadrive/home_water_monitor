package com.juca.hydroponic.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juca.hydroponic.R

val Forest = Color(0xFF285943)
val Leaf = Color(0xFFDDEBD1)
val Cream = Color(0xFFFAF9F2)
val Muted = Color(0xFF526451)
val NumberStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 30.sp, lineHeight = 38.sp)
private val Handwriting = FontFamily(Font(R.font.gamja_flower))
private val Body = FontFamily(Font(R.font.gowun_dodum))
private fun heading(size: Int) = TextStyle(fontFamily = Handwriting, fontSize = size.sp, lineHeight = (size + 5).sp)
private fun body(size: Int) = TextStyle(fontFamily = Body, fontSize = size.sp, lineHeight = (size + 7).sp)

@Composable fun GardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest, onPrimary = Color.White, primaryContainer = Leaf, onPrimaryContainer = Forest,
            secondary = Color(0xFF566D45), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEAF0DE), onSecondaryContainer = Forest,
            tertiary = Color(0xFF71602E), tertiaryContainer = Color(0xFFF7EDC9), onTertiaryContainer = Color(0xFF514316),
            background = Cream, onBackground = Color(0xFF263C2C),
            surface = Cream, onSurface = Color(0xFF263C2C),
            surfaceVariant = Color(0xFFEEF2E6), onSurfaceVariant = Muted,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4E8),
            surfaceContainer = Color(0xFFECF1E2), surfaceContainerHigh = Color(0xFFE4ECD9),
            surfaceContainerHighest = Color(0xFFDCE7D1), outline = Color(0xFF74856B), outlineVariant = Color(0xFFD4DEC9),
            error = Color(0xFF9B3730), errorContainer = Color(0xFFFFE8DE), onErrorContainer = Color(0xFF61221E)
        ),
        typography = Typography(
            displayLarge = heading(48), displayMedium = heading(42), displaySmall = heading(38),
            headlineLarge = heading(36), headlineMedium = heading(32), headlineSmall = heading(28),
            titleLarge = heading(26), titleMedium = body(18), titleSmall = body(16),
            bodyLarge = body(16), bodyMedium = body(15), bodySmall = body(13),
            labelLarge = body(15), labelMedium = body(13), labelSmall = body(12)
        ),
        shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content
    )
}
