package com.sarat.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Repository
import com.sarat.app.domain.DayRecord
import com.sarat.app.domain.DayStatus
import com.sarat.app.domain.Nutrition
import com.sarat.app.ui.components.MainBottomBar
import com.sarat.app.ui.components.Tab
import com.sarat.app.ui.home.WeighInBanner
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryState(
    val days: List<DayRecord>,
    val adherentDays: Int,
    val evaluatedDays: Int,
    val streak: Int,
    val target: Int,
    val weekNumber: Int,
    val weighInDue: Boolean,
)

class HistoryViewModel(repo: Repository) : ViewModel() {
    val state: StateFlow<HistoryState?> = combine(
        repo.profile, repo.weights, repo.dailyTotals, Dates.todayFlow(),
    ) { profile, weights, totals, today ->
        if (profile == null) return@combine null
        val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
        val plan = Nutrition.plan(profile, current, today)
        val days = Nutrition.buildHistory(totals, profile.startEpochDay, today, plan.dailyTarget, plan.minimumCalories)
        // اليوم الحالي ما ينحسب إذا بعده مستمر وتحت الحد الأدنى
        val evaluated = days.filter { it.status != DayStatus.IN_PROGRESS }
        HistoryState(
            days = days,
            adherentDays = evaluated.count { it.status == DayStatus.ON_TARGET },
            evaluatedDays = evaluated.size,
            streak = Nutrition.currentStreak(days),
            target = plan.dailyTarget,
            weekNumber = ((today - profile.startEpochDay) / 7 + 1).toInt(),
            weighInDue = Nutrition.isWeighInDue(weights.lastOrNull()?.epochDay ?: profile.startEpochDay, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onTab: (Tab) -> Unit, onWeighIn: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = C.Ground, bottomBar = { MainBottomBar(Tab.HISTORY, onTab) }) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("السجل", style = MaterialTheme.typography.headlineSmall)
                    Text("الأسبوع ${s.weekNumber} من الخطة", fontSize = 13.sp, color = C.Muted)
                }
            }
            item {
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(C.Ink).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("أيام الالتزام", fontSize = 12.sp, color = C.OnDarkMuted)
                        Text("${s.adherentDays} من ${s.evaluatedDays}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White)
                            .border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("أيام متتالية", fontSize = 12.sp, color = C.Muted)
                        Text("${s.streak}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Green)
                    }
                }
            }
            if (s.weighInDue) item { WeighInBanner(onWeighIn) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("كل الأيام", style = MaterialTheme.typography.titleMedium)
                    Text("الهدف ${s.target} سعرة باليوم", fontSize = 12.sp, color = C.Muted)
                }
            }
            items(s.days, key = { it.epochDay }) { DayRow(it, isToday = it == s.days.first()) }
        }
    }
}

private data class StatusLook(val bg: Color, val rowBg: Color, val border: Color, val text: Color, val icon: ImageVector, val label: String)

private fun look(status: DayStatus) = when (status) {
    DayStatus.ON_TARGET -> StatusLook(C.Green, Color.White, C.Line, C.GreenDark, Icons.Outlined.Check, "ملتزم")
    DayStatus.OVER -> StatusLook(C.Orange, Color(0xFFFFF8F1), Color(0xFFEBC9A3), C.OrangeText, Icons.Outlined.Add, "زيادة")
    DayStatus.TOO_LOW -> StatusLook(C.Red, C.RedSoft, Color(0xFFE8B4AE), C.RedText, Icons.Outlined.PriorityHigh, "أقل من الحد الآمن")
    DayStatus.IN_PROGRESS -> StatusLook(C.Muted, Color.White, C.Line, C.Muted, Icons.Outlined.Schedule, "اليوم مستمر")
    DayStatus.NO_DATA -> StatusLook(C.Border, Color.White, C.Line, C.Muted, Icons.Outlined.Remove, "بدون تسجيل")
}

@Composable
private fun DayRow(day: DayRecord, isToday: Boolean) {
    val l = look(day.status)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(l.rowBg)
            .border(1.dp, l.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(l.bg).semantics { contentDescription = l.label },
            contentAlignment = Alignment.Center,
        ) { Icon(l.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(Dates.shortDay(day.epochDay) + if (isToday) " · اليوم" else "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(day.message, fontSize = 12.sp, color = l.text)
        }
        Text(if (day.consumed > 0) "${day.consumed}" else "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
