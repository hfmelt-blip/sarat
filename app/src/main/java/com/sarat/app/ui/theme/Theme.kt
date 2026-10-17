package com.sarat.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Green = Color(0xFF1F6F54)
    val GreenDark = Color(0xFF164F3C)
    val GreenSoft = Color(0xFFE1ECE6)
    val Ink = Color(0xFF1B1F1D)
    val InkSoft = Color(0xFF2A302D)
    val Muted = Color(0xFF5B625E)
    val OnDarkMuted = Color(0xFFC9CFCB)
    val Ground = Color(0xFFF6F5F0)
    val Line = Color(0xFFECEAE2)
    val Border = Color(0xFFDEDCD3)
    val Orange = Color(0xFFC2611F)
    val OrangeText = Color(0xFF7A3E0F)
    val OrangeSoft = Color(0xFFFBEBD9)
    val Beige = Color(0xFFF1E6D8)
    val BeigeText = Color(0xFF5A3210)
    val Red = Color(0xFFB3392E)
    val RedText = Color(0xFF8E2A22)
    val RedSoft = Color(0xFFF9E3E1)
    val Blue = Color(0xFF3A5BA0)
}

private val colors = lightColorScheme(
    primary = C.Green,
    onPrimary = Color.White,
    primaryContainer = C.GreenSoft,
    onPrimaryContainer = C.GreenDark,
    secondary = C.Orange,
    background = C.Ground,
    onBackground = C.Ink,
    surface = Color.White,
    onSurface = C.Ink,
    surfaceVariant = C.Line,
    onSurfaceVariant = C.Muted,
    outline = C.Border,
    error = C.Red,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun SaratTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
