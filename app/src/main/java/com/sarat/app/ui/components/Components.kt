package com.sarat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.data.gemini.Verdict
import com.sarat.app.ui.theme.C

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    background: Color = C.Card,
    border: Color = C.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Green),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, C.Ink),
    ) { Text(text, color = C.Ink, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StepHeader(step: Int, total: Int, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < step) C.Green else C.Border)
                )
            }
        }
        Text("الخطوة $step من $total", style = MaterialTheme.typography.bodySmall, color = C.Muted)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    error: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val allowed = if (decimal) new.filter { it.isDigit() || it == '.' } else new.filter { it.isDigit() }
            onValueChange(allowed.take(6))
        },
        label = { Text(label) },
        suffix = { Text(suffix, color = C.Muted) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.Beige)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = C.BeigeText, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = C.BeigeText)
    }
}

@Composable
fun CalorieRing(consumed: Int, target: Int, modifier: Modifier = Modifier) {
    val remaining = target - consumed
    val fraction = if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
    val over = remaining < 0
    Box(
        modifier = modifier
            .size(200.dp)
            .semantics { contentDescription = "تم استهلاك $consumed من $target سعرة" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(C.GreenSoft, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                if (over) C.Orange else C.Green, -90f, 360f * fraction, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (over) "زيادة اليوم" else "متبقي لليوم", style = MaterialTheme.typography.bodySmall, color = C.Muted)
            Text(
                "${kotlin.math.abs(remaining)}",
                fontSize = 44.sp, fontWeight = FontWeight.Bold,
                color = if (over) C.OrangeText else C.Ink,
            )
            Text("سعرة حرارية", style = MaterialTheme.typography.bodySmall, color = C.Muted)
        }
    }
}

@Composable
fun MacroBar(label: String, value: Double, target: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${value.toInt()}/${target}غ", style = MaterialTheme.typography.bodySmall, color = C.Muted)
        }
        LinearProgressIndicator(
            progress = { if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = C.Line,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

data class VerdictStyle(val bg: Color, val border: Color, val text: Color, val icon: ImageVector)

fun verdictStyle(v: Verdict) = when (v) {
    Verdict.GOOD -> VerdictStyle(C.GreenSoft, C.GoodBorder, C.GreenDark, Icons.Outlined.CheckCircle)
    Verdict.OK_WITH_CHANGES -> VerdictStyle(C.OrangeSoft, C.WarnBorder, C.OrangeText, Icons.Outlined.WarningAmber)
    Verdict.NOT_SUITABLE -> VerdictStyle(C.RedSoft, C.BadBorder, C.RedText, Icons.Outlined.Cancel)
}

@Composable
fun VerdictCard(verdict: Verdict, goalsLabel: String, reason: String, suggestion: String) {
    val s = verdictStyle(verdict)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(s.bg)
            .border(1.dp, s.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(s.icon, contentDescription = null, tint = s.text, modifier = Modifier.size(20.dp))
            Text(verdict.label, color = s.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (goalsLabel.isNotBlank()) Text("· $goalsLabel", color = s.text, fontSize = 12.sp)
        }
        if (reason.isNotBlank()) Text(reason, color = s.text, fontSize = 13.sp, lineHeight = 21.sp)
        if (suggestion.isNotBlank()) {
            Text("الاقتراح: $suggestion", color = s.text, fontSize = 13.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun VerdictBadge(verdict: Verdict) {
    val s = verdictStyle(verdict)
    Text(
        verdict.label.removeSuffix(" لهدفك"),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(s.bg)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        color = s.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun FitDot(fit: String) {
    val (color, label) = when (fit) {
        "GOOD" -> C.Green to "مناسب لهدفك"
        "AVOID" -> C.Red to "غير مناسب لهدفك"
        else -> C.Orange to "باعتدال"
    }
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = label }
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) C.InkSoft else C.Ground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = if (dark) C.OnDarkMuted else C.Muted)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (dark) Color.White else C.Ink)
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("home", "اليوم", Icons.Outlined.Home),
    HISTORY("history", "السجل", Icons.Outlined.CalendarMonth),
    PROGRESS("progress", "التقدم", Icons.Outlined.ShowChart),
    PROFILE("edit_profile", "الملف", Icons.Outlined.Person),
}

@Composable
fun MainBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = C.Card) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { if (tab != current) onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = C.Green,
                    selectedTextColor = C.Green,
                    indicatorColor = C.GreenSoft,
                    unselectedIconColor = C.Muted,
                    unselectedTextColor = C.Muted,
                ),
            )
        }
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))
