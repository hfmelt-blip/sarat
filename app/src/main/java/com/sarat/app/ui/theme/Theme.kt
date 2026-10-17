package com.sarat.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.R

/**
 * ألوان التطبيق بالوضعين الفاتح والداكن. [dark] يُضبط من MainActivity حسب إعداد الموبايل،
 * وتغيّر الوضع يعيد إنشاء الشاشة فتنقرأ الألوان من جديد.
 */
object C {
    var dark: Boolean = false

    private fun pick(light: Long, night: Long) = Color(if (dark) night else light)

    val Green get() = pick(0xFF1F6F54, 0xFF2F8F6C)
    val GreenDark get() = pick(0xFF164F3C, 0xFFA8E3C8)
    val GreenSoft get() = pick(0xFFE1ECE6, 0xFF1E3A30)
    val Ink get() = pick(0xFF1B1F1D, 0xFFECEFED)
    /** خلفية البطاقات الداكنة (الخطة والملخص) بالوضعين. */
    val Hero get() = pick(0xFF1B1F1D, 0xFF26302B)
    val InkSoft get() = pick(0xFF2A302D, 0xFF33403A)
    val Muted get() = pick(0xFF5B625E, 0xFFA3ABA6)
    val OnDarkMuted get() = Color(0xFFC9CFCB)
    val Ground get() = pick(0xFFF6F5F0, 0xFF121514)
    /** سطح البطاقات والأزرار البيضاء. */
    val Card get() = pick(0xFFFFFFFF, 0xFF1C211F)
    val Line get() = pick(0xFFECEAE2, 0xFF2C3330)
    val Border get() = pick(0xFFDEDCD3, 0xFF3A423E)
    val Orange get() = pick(0xFFC2611F, 0xFFE08A4F)
    val OrangeText get() = pick(0xFF7A3E0F, 0xFFF2C29A)
    val OrangeSoft get() = pick(0xFFFBEBD9, 0xFF3A2A1C)
    val Beige get() = pick(0xFFF1E6D8, 0xFF33291F)
    val BeigeText get() = pick(0xFF5A3210, 0xFFEBCFA8)
    val Red get() = pick(0xFFB3392E, 0xFFE0675C)
    val RedText get() = pick(0xFF8E2A22, 0xFFF2B1AA)
    val RedSoft get() = pick(0xFFF9E3E1, 0xFF3B2220)
    val Blue get() = pick(0xFF3A5BA0, 0xFF7F9FE0)
    val GoodBorder get() = pick(0xFFB9D6C7, 0xFF2F5A49)
    val WarnBorder get() = pick(0xFFEBC9A3, 0xFF5A4128)
    val BadBorder get() = pick(0xFFE8B4AE, 0xFF5C3330)
    val OverRow get() = pick(0xFFFFF8F1, 0xFF2A2119)
    val InfoBg get() = pick(0xFFE6EBF5, 0xFF1E2638)
    val InfoIcon get() = pick(0xFF2B4C8C, 0xFF9DB5EA)
    val InfoText get() = pick(0xFF1F3766, 0xFFC9D6F2)
    /** عناصر الانتظار (Skeleton). */
    val Skeleton get() = pick(0xFFE7E5DD, 0xFF2A312E)
}

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

private fun scheme() = if (C.dark) {
    darkColorScheme(
        primary = C.Green, onPrimary = Color.White,
        primaryContainer = C.GreenSoft, onPrimaryContainer = C.GreenDark,
        secondary = C.Orange,
        background = C.Ground, onBackground = C.Ink,
        surface = C.Card, onSurface = C.Ink,
        surfaceVariant = C.Line, onSurfaceVariant = C.Muted,
        surfaceContainer = C.Card, surfaceContainerHigh = C.Card,
        outline = C.Border, error = C.Red,
    )
} else {
    lightColorScheme(
        primary = C.Green, onPrimary = Color.White,
        primaryContainer = C.GreenSoft, onPrimaryContainer = C.GreenDark,
        secondary = C.Orange,
        background = C.Ground, onBackground = C.Ink,
        surface = C.Card, onSurface = C.Ink,
        surfaceVariant = C.Line, onSurfaceVariant = C.Muted,
        surfaceContainer = C.Card, surfaceContainerHigh = C.Card,
        outline = C.Border, error = C.Red,
    )
}

private val base = Typography()

/** كل النصوص بخط تجوال، حتى اللي ما تحدد لها نمط. */
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Tajawal),
    displayMedium = base.displayMedium.copy(fontFamily = Tajawal),
    displaySmall = base.displaySmall.copy(fontFamily = Tajawal),
    headlineLarge = base.headlineLarge.copy(fontFamily = Tajawal),
    headlineMedium = TextStyle(fontFamily = Tajawal, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Tajawal, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Tajawal),
    titleMedium = TextStyle(fontFamily = Tajawal, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Tajawal),
    bodyLarge = TextStyle(fontFamily = Tajawal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Tajawal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Tajawal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Tajawal, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Tajawal),
    labelSmall = base.labelSmall.copy(fontFamily = Tajawal),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun SaratTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = typography, shapes = shapes) {
        // النصوص اللي تستخدم Text بدون نمط تاخذ bodyLarge، فهيچ يصير الخط تجوال بكل مكان
        // Surface يعطي لون النص الافتراضي حسب الوضع (فاتح/داكن) لكل الشاشات
        androidx.compose.material3.Surface(color = C.Ground, contentColor = C.Ink) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge, content)
        }
    }
}
