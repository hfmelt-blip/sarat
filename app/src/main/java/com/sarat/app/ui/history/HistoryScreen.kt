package com.sarat.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Meal
import com.sarat.app.data.Repository
import com.sarat.app.data.gemini.Verdict
import com.sarat.app.domain.DayRecord
import com.sarat.app.domain.DayStatus
import com.sarat.app.domain.Nutrition
import com.sarat.app.ui.components.MainBottomBar
import com.sarat.app.ui.components.MealThumb
import com.sarat.app.ui.components.Tab
import com.sarat.app.ui.components.VerdictBadge
import com.sarat.app.ui.home.WeighInBanner
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryState(
    val days: Map<Long, DayRecord>,
    val adherentDays: Int,
    val evaluatedDays: Int,
    val streak: Int,
    val target: Int,
    val weekNumber: Int,
    val weighInDue: Boolean,
    val today: Long,
    val startDay: Long,
    val month: YearMonth,
    val selected: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repo: Repository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(Dates.todayEpoch())

    private val base = combine(repo.profile, repo.weights, repo.dailyTotals, Dates.todayFlow()) { profile, weights, totals, today ->
        if (profile == null) return@combine null
        val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
        val plan = Nutrition.plan(profile, current, today)
        val days = Nutrition.buildHistory(totals, profile.startEpochDay, today, plan.dailyTarget, plan.minimumCalories)
        val evaluated = days.filter { it.status != DayStatus.IN_PROGRESS }
        HistoryState(
            days = days.associateBy { it.epochDay },
            adherentDays = evaluated.count { it.status == DayStatus.ON_TARGET },
            evaluatedDays = evaluated.size,
            streak = Nutrition.currentStreak(days),
            target = plan.dailyTarget,
            weekNumber = ((today - profile.startEpochDay) / 7 + 1).toInt(),
            weighInDue = Nutrition.isWeighInDue(weights.lastOrNull()?.epochDay ?: profile.startEpochDay, today),
            today = today,
            startDay = profile.startEpochDay,
            month = YearMonth.now(),
            selected = today,
        )
    }

    val state: StateFlow<HistoryState?> = combine(base, month, selected) { s, m, sel ->
        s?.copy(month = m, selected = sel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedMeals: StateFlow<List<Meal>> = selected
        .flatMapLatest { repo.mealsForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(day: Long) { selected.value = day }
    fun shiftMonth(delta: Long) { month.value = month.value.plusMonths(delta) }
}

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("ar-IQ"))

/** الأسبوع يبدأ بالسبت. */
private val weekDays = listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")

@Composable
fun HistoryScreen(vm: HistoryViewModel, onTab: (Tab) -> Unit, onWeighIn: () -> Unit, onAllMeals: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val meals by vm.selectedMeals.collectAsStateWithLifecycle()
    Scaffold(containerColor = C.Ground, bottomBar = { MainBottomBar(Tab.HISTORY, onTab) }) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("السجل", style = MaterialTheme.typography.headlineSmall, color = C.Ink)
                    Text("الأسبوع ${s.weekNumber} من الخطة", fontSize = 13.sp, color = C.Muted)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(C.Hero).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("أيام الالتزام", fontSize = 12.sp, color = C.OnDarkMuted)
                        Text("${s.adherentDays} من ${s.evaluatedDays}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(C.Card)
                            .border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("أيام متتالية", fontSize = 12.sp, color = C.Muted)
                        Text("${s.streak}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Green)
                    }
                }
            }
            if (s.weighInDue) item { WeighInBanner(onWeighIn) }
            item { MonthCalendar(s, onSelect = vm::select, onShift = vm::shiftMonth) }
            item { Legend() }
            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = onAllMeals, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                ) { Text("جدول كل وجباتي (المكونات والبروتين والكارب والدهون)", color = C.Green, fontSize = 13.sp) }
            }
            item { DayHeader(s) }
            if (meals.isEmpty()) {
                item { Text("ما مسجّل أكل لهذا اليوم.", color = C.Muted, fontSize = 14.sp) }
            }
            items(meals, key = { it.id }) { MealLine(it, onAllMeals) }
        }
    }
}

private fun statusColor(status: DayStatus?): Color? = when (status) {
    DayStatus.ON_TARGET -> C.Green
    DayStatus.OVER -> C.Orange
    DayStatus.TOO_LOW -> C.Red
    else -> null
}

@Composable
private fun MonthCalendar(s: HistoryState, onSelect: (Long) -> Unit, onShift: (Long) -> Unit) {
    val first = s.month.atDay(1)
    val lead = (first.dayOfWeek.value + 1) % 7 // السبت = 0
    val count = s.month.lengthOfMonth()
    val canBack = s.month.isAfter(YearMonth.from(LocalDate.ofEpochDay(s.startDay)))
    val canNext = s.month.isBefore(YearMonth.from(LocalDate.ofEpochDay(s.today)))

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "الشهر السابق", tint = if (canBack) C.Ink else C.Border)
            }
            Text(
                s.month.format(monthFormat), modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.Ink,
            )
            IconButton(onClick = { onShift(1) }, enabled = canNext) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "الشهر الجاي", tint = if (canNext) C.Ink else C.Border)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = C.Muted)
            }
        }
        val cells = lead + count
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (index in 1..count) {
                            val epoch = s.month.atDay(index).toEpochDay()
                            DayCell(index, epoch, s, onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(dayNumber: Int, epoch: Long, s: HistoryState, onSelect: (Long) -> Unit) {
    val record = s.days[epoch]
    val inPlan = epoch in s.startDay..s.today
    val fill = statusColor(record?.status)
    val isToday = epoch == s.today
    val selected = epoch == s.selected
    val label = record?.message ?: ""
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(fill ?: if (inPlan && record?.status == DayStatus.NO_DATA) C.Line else Color.Transparent)
            .border(
                width = if (selected) 2.5.dp else if (isToday) 1.5.dp else 0.dp,
                color = if (selected) C.Ink else if (isToday) C.Green else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(enabled = inPlan) { onSelect(epoch) }
            .semantics { contentDescription = "$dayNumber ${if (label.isNotBlank()) "، $label" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$dayNumber",
            fontSize = 13.sp,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                fill != null -> Color.White
                inPlan -> C.Ink
                else -> C.Border
            },
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(C.Green, "ملتزم")
        LegendItem(C.Orange, "زيادة")
        LegendItem(C.Red, "أقل من الآمن")
        LegendItem(C.Line, "بدون تسجيل")
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, fontSize = 11.sp, color = C.Muted)
    }
}

@Composable
private fun DayHeader(s: HistoryState) {
    val record = s.days[s.selected]
    val tint = statusColor(record?.status) ?: C.Muted
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                Dates.longDay(s.selected) + if (s.selected == s.today) " · اليوم" else "",
                style = MaterialTheme.typography.titleMedium, color = C.Ink,
            )
            Text("${record?.consumed ?: 0} / ${s.target}", fontSize = 13.sp, color = C.Muted)
        }
        if (record != null) Text(record.message, fontSize = 13.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MealLine(meal: Meal, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealThumb(meal.id, meal.source == "PHOTO", size = 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(meal.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Dates.time(meal.minuteOfDay), fontSize = 12.sp, color = C.Muted)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${meal.calories}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink)
            VerdictBadge(Verdict.from(meal.verdict))
        }
    }
}
