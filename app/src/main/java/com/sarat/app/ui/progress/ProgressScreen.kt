package com.sarat.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarat.app.data.db.WeightEntity
import com.sarat.app.ui.components.InfoNote
import com.sarat.app.ui.components.MainBottomBar
import com.sarat.app.ui.components.NumberField
import com.sarat.app.ui.components.PrimaryButton
import com.sarat.app.ui.components.SectionCard
import com.sarat.app.ui.components.StatTile
import com.sarat.app.ui.components.Tab
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates
import com.sarat.app.util.fmt
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ProgressScreen(vm: ProgressViewModel, onTab: (Tab) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = C.Ground, bottomBar = { MainBottomBar(Tab.PROGRESS, onTab) }) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("التقدم", style = MaterialTheme.typography.headlineSmall)

            // ملخص الخطة الحالية
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Ink).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("وزنك الحالي", "${s.currentWeight.fmt()} كغم", Modifier.weight(1f), dark = true)
                    StatTile("الهدف", "${s.profile.targetWeightKg.fmt()} كغم", Modifier.weight(1f), dark = true)
                    StatTile("باقي", "${s.plan.remainingKg.fmt()} كغم", Modifier.weight(1f), dark = true)
                }
                Text(
                    s.plan.etaEpochDay?.let { "الموعد المتوقع للوصول: حوالي ${Dates.fullDate(it)}" }
                        ?: "وصلت لهدفك! تكدر تحدد هدف جديد من الملف.",
                    color = Color.White, fontSize = 14.sp,
                )
            }

            SectionCard {
                Text("وزنك عبر الوقت", style = MaterialTheme.typography.titleMedium)
                if (s.weights.size >= 2) {
                    WeightChart(s.weights, s.profile.targetWeightKg)
                } else {
                    Text("بعد أول قياس جديد يطلع هنا منحنى وزنك.", color = C.Muted, fontSize = 13.sp)
                }
            }

            // إدخال القياس
            SectionCard {
                Text("قياس الوزن", style = MaterialTheme.typography.titleMedium)
                Text(
                    "آخر قياس: ${s.currentWeight.fmt()} كغم، قبل ${s.today - s.lastWeighIn} يوم. " +
                        "قيس الصبح قبل الأكل وبنفس الميزان.",
                    fontSize = 13.sp, color = C.Muted,
                )
                NumberField(
                    "وزنك اليوم", vm.weightInput, vm::onInput, "كغم",
                    Modifier.fillMaxWidth(), decimal = true,
                    error = vm.weightInput.isNotEmpty() && vm.inputValue == null,
                )
                PrimaryButton("احسب التغيير", vm::calculate, enabled = vm.inputValue != null)
                if (vm.saved) Text("تم الحفظ وتحديث خطتك ✓", color = C.GreenDark, fontWeight = FontWeight.SemiBold)
            }

            vm.result?.let { r -> ResultSection(r, onSave = vm::save) }
        }
    }
}

@Composable
private fun ResultSection(r: WeighInResult, onSave: () -> Unit) {
    val c = r.calibration
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (c.lostKg >= 0) "نزلت خلال ${c.days} يوم" else "زاد وزنك خلال ${c.days} يوم", fontWeight = FontWeight.SemiBold)
            Text(
                "${abs(c.lostKg).fmt()} كغم", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = if (c.lostKg >= 0) C.Green else C.OrangeText,
            )
        }
        if (c.averageIntake > 0) {
            val maxV = max(max(c.expectedLostKg, c.lostKg), 0.1)
            CompareBar("المتوقع", c.expectedLostKg, maxV, C.Muted)
            CompareBar("الفعلي", c.lostKg, maxV, C.Green)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("متوسط استهلاكك", "${c.averageIntake} سعرة", Modifier.weight(1f))
                StatTile("العجز الفعلي", "${c.actualDailyDeficit} سعرة/يوم", Modifier.weight(1f))
            }
        }
        Text(
            when {
                !c.reliable -> "البيانات بعدها قليلة (سجلت ${c.loggedDays} من ${c.days} يوم). نحفظ الوزن ونحدّث الخطة عليه، " +
                    "ونعدّل تقدير احتياجك لما تكمّل أسبوع تسجيل على الأقل."
                c.tdeeError > 50 -> "جسمك يحرق أكثر من المحسوب بحوالي ${c.tdeeError} سعرة باليوم، فعدّلنا احتياجك."
                c.tdeeError < -50 -> "جسمك يحرق أقل من المحسوب بحوالي ${-c.tdeeError} سعرة باليوم، فعدّلنا احتياجك حتى تبقى الخطة دقيقة."
                else -> "النزول مطابق تقريباً للخطة. استمر هيج!"
            },
            fontSize = 13.sp, color = C.Muted, lineHeight = 21.sp,
        )
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Ink).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val projected = r.projectedEtaEpochDay
        val oldEta = r.oldPlan.etaEpochDay
        Text("موعد الوصول للهدف حسب نزولك الفعلي", color = C.OnDarkMuted, fontSize = 13.sp)
        if (r.newPlan.remainingKg <= 0) {
            Text("وصلت لهدفك! مبروك", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        } else if (projected != null) {
            Text("حوالي ${Dates.fullDate(projected)}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (oldEta != null && abs(projected - oldEta) >= 7) {
                val weeks = abs(projected - oldEta) / 7
                Text(
                    if (projected < oldEta) "أبكر بـ $weeks أسبوع من التقدير السابق" else "أبعد بـ $weeks أسبوع من التقدير السابق",
                    color = C.OnDarkMuted, fontSize = 13.sp,
                )
            }
        } else {
            Text("الوزن ما نزل بهذي الفترة، فما نكدر نقدّر الموعد بعد.", color = Color.White, fontSize = 15.sp)
        }
        Text(
            "هدفك اليومي الجديد: ${r.newPlan.dailyTarget} سعرة (عجز ${r.newPlan.dailyDeficit} سعرة)",
            color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
        )
        if (r.newPlan.limitedByMinimum) {
            Text("ما ننزل السعرات تحت ${r.newPlan.minimumCalories} حفاظاً على صحتك، فالمدة ممكن تطول.", color = C.OnDarkMuted, fontSize = 12.sp)
        }
    }

    InfoNote("أول أسابيع ينزل بيها ماء أيضاً، والوزن يتذبذب يومياً، فالتطبيق ياخذ نص الفرق بكل قياس حتى يصير التقدير أدق تدريجياً.")
    PrimaryButton("احفظ وحدّث الخطة", onSave)
}

@Composable
private fun CompareBar(label: String, value: Double, maxValue: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 12.sp, color = C.Muted, modifier = Modifier.width(56.dp))
        LinearProgressIndicator(
            progress = { (max(value, 0.0) / maxValue).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = color, trackColor = C.Line, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        )
        Text("${value.fmt()} كغم", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun WeightChart(weights: List<WeightEntity>, target: Double) {
    val first = weights.first().epochDay
    val last = weights.last().epochDay
    val minKg = minOf(weights.minOf { it.kg }, target) - 1
    val maxKg = weights.maxOf { it.kg } + 1
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "منحنى الوزن من ${weights.first().kg.fmt()} إلى ${weights.last().kg.fmt()} كغم" }
    ) {
        val w = size.width
        val h = size.height
        fun x(day: Long) = if (last == first) w / 2 else ((day - first).toFloat() / (last - first)) * w
        fun y(kg: Double) = (h - ((kg - minKg) / (maxKg - minKg)).toFloat() * h)

        val ty = y(target)
        drawLine(C.Orange, Offset(0f, ty), Offset(w, ty), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))

        val path = Path()
        weights.forEachIndexed { i, e ->
            val px = x(e.epochDay)
            val py = y(e.kg)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, C.Green, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        weights.forEach { e -> drawCircle(C.Green, radius = 5.dp.toPx(), center = Offset(x(e.epochDay), y(e.kg))) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Dates.shortDay(weights.first().epochDay), fontSize = 11.sp, color = C.Muted)
        Text("— الهدف ${target.fmt()} كغم", fontSize = 11.sp, color = C.OrangeText)
        Text(Dates.shortDay(weights.last().epochDay), fontSize = 11.sp, color = C.Muted)
    }
}
