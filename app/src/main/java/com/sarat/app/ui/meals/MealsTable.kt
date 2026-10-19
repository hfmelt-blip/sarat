package com.sarat.app.ui.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarat.app.data.Meal
import com.sarat.app.ui.kitchen.KitchenHeader
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates

private fun g(v: Double) = "${v.toInt()}"

/** جدول كل الوجبات السابقة، مجمّعة حسب اليوم، وكل وجبة تنفتح على مكوناتها. */
@Composable
fun MealsTableScreen(vm: MealsViewModel, onBack: () -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val open = remember { mutableStateMapOf<Long, Boolean>() }
    LazyColumn(
        Modifier.fillMaxSize().background(C.Ground).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { KitchenHeader("كل وجباتي", "اضغط على أي وجبة حتى تشوف مكوناتها", onBack) }
        if (groups.isEmpty()) {
            item { Text("ما كو وجبات مسجلة بعد.", color = C.Muted, fontSize = 14.sp) }
        }
        groups.forEach { day ->
            item(key = "d${day.epochDay}") {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Dates.longDay(day.epochDay), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.Ink)
                    Text(
                        "${day.calories} سعرة · بروتين ${g(day.protein)}غ · كارب ${g(day.carbs)}غ · دهون ${g(day.fat)}غ",
                        fontSize = 12.sp, color = C.Muted,
                    )
                }
            }
            items(day.meals, key = { "m${it.id}" }) { meal ->
                MealCard(meal, open[meal.id] == true) { open[meal.id] = open[meal.id] != true }
            }
        }
    }
}

@Composable
private fun MealCard(meal: Meal, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(C.Card, RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(meal.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Dates.time(meal.minuteOfDay)} · ${meal.calories} سعرة · ب ${g(meal.proteinG)} · ك ${g(meal.carbsG)} · د ${g(meal.fatG)}",
                    fontSize = 12.sp, color = C.Muted,
                )
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = C.Muted, modifier = Modifier.size(22.dp))
        }
        if (expanded) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                TableRow(bold = true, shaded = true, "المكوّن", "سعرات", "بروتين", "كارب", "دهون")
                meal.items.forEach { i ->
                    TableRow(false, false, i.name + if (i.portion.isNotBlank()) " (${i.portion})" else "", "${i.calories}", g(i.proteinG), g(i.carbsG), g(i.fatG))
                }
                TableRow(bold = true, shaded = true, "المجموع", "${meal.calories}", g(meal.proteinG), g(meal.carbsG), g(meal.fatG))
            }
        }
    }
}

@Composable
private fun TableRow(bold: Boolean, shaded: Boolean, name: String, kcal: String, p: String, c: String, f: String) {
    Row(
        Modifier.fillMaxWidth().background(if (shaded) C.GreenSoft else C.Card).padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(name, 2.4f, bold, TextAlign.Start)
        Cell(kcal, 1f, bold)
        Cell(p, 1f, bold)
        Cell(c, 1f, bold)
        Cell(f, 1f, bold)
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, bold: Boolean, align: TextAlign = TextAlign.Center) {
    Text(
        text, modifier = Modifier.weight(weight), textAlign = align,
        fontSize = 12.sp, color = C.Ink, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}
