package com.sarat.app.ui.onboarding

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.domain.Activity
import com.sarat.app.domain.DietGoal
import com.sarat.app.domain.Sex
import com.sarat.app.ui.components.InfoNote
import com.sarat.app.ui.components.NumberField
import com.sarat.app.ui.components.PrimaryButton
import com.sarat.app.ui.components.StatTile
import com.sarat.app.ui.components.StepHeader
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates
import com.sarat.app.util.fmt

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit, onExit: (() -> Unit)?) {
    BackHandler(enabled = vm.step > 1 || onExit != null) {
        if (!vm.back()) onExit?.invoke()
    }

    if (!vm.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (vm.step) {
                1 -> StepProfile(vm)
                2 -> StepDietGoal(vm)
                else -> StepWeightGoal(vm)
            }
        }
        Box(Modifier.padding(top = 12.dp)) {
            when (vm.step) {
                1 -> PrimaryButton("التالي: هدف النظام", vm::next, enabled = vm.step1Valid)
                2 -> PrimaryButton("التالي: الوزن المستهدف", vm::next, enabled = vm.step2Valid)
                else -> PrimaryButton(
                    if (vm.editMode) "احفظ التعديلات" else "ابدأ الخطة",
                    { vm.save(onDone) },
                    enabled = vm.step3Valid,
                )
            }
        }
    }
}

@Composable
private fun StepProfile(vm: OnboardingViewModel) {
    StepHeader(1, 3, "خلّينا نعرفك", "نحسب منها احتياج جسمك اليومي من السعرات.")

    OutlinedTextField(
        value = vm.name,
        onValueChange = { vm.name = it.take(30) },
        label = { Text("اسمك") },
        placeholder = { Text("مثلاً: حيدر") },
        supportingText = { Text("حتى نرحب بيك ونشجعك كل يوم") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    Text("الجنس", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Sex.entries.forEach { s ->
            ChoiceBox(selected = vm.sex == s, onClick = { vm.sex = s }, modifier = Modifier.weight(1f)) {
                Text(s.label, Modifier.padding(vertical = 14.dp).fillMaxWidth(), fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField("العمر", vm.age, { vm.age = it }, "سنة", Modifier.weight(1f), error = vm.ageError != null)
        NumberField("الطول", vm.height, { vm.height = it }, "سم", Modifier.weight(1f), decimal = true, error = vm.heightError != null)
        NumberField("الوزن", vm.weight, { vm.weight = it }, "كغم", Modifier.weight(1f), decimal = true, error = vm.weightError != null)
    }
    listOfNotNull(vm.ageError, vm.heightError, vm.weightError).forEach {
        Text(it, color = C.Red, fontSize = 13.sp)
    }

    Text("مستوى نشاطك اليومي", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Activity.entries.forEach { a ->
            val selected = vm.activity == a
            ChoiceBox(selected = selected, onClick = { vm.activity = a }, role = Role.RadioButton) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = C.Green))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(a.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(a.hint, fontSize = 12.sp, color = C.Muted)
                    }
                }
            }
        }
    }

    vm.tdeePreview?.let {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, C.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("احتياجك اليومي التقريبي", color = C.Muted, fontSize = 14.sp)
            Text("$it سعرة", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

private fun goalIcon(goal: DietGoal): ImageVector = when (goal) {
    DietGoal.INSULIN_RESISTANCE -> Icons.Outlined.Bloodtype
    DietGoal.HYPOTHYROID -> Icons.Outlined.Spa
    DietGoal.OBESITY_LIPIDS -> Icons.Outlined.MonitorHeart
}

@Composable
private fun StepDietGoal(vm: OnboardingViewModel) {
    val context = LocalContext.current
    StepHeader(2, 3, "شنو هدفك من النظام؟", "على أساسه نختار نوع الأكل المناسب ونقيّم كل وجبة. تكدر تختار أكثر من هدف.")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DietGoal.entries.forEach { goal ->
            val checked = goal in vm.goals
            ChoiceBox(selected = checked, onClick = { vm.toggleGoal(goal) }, role = Role.Checkbox) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) { Icon(goalIcon(goal), contentDescription = null, tint = C.Green) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(goal.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(goal.description, fontSize = 13.sp, color = C.Muted, lineHeight = 20.sp)
                    }
                    Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = C.Green))
                }
            }
        }
    }

    if (DietGoal.HYPOTHYROID in vm.goals) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("وقت أخذ دواء الغدة", style = MaterialTheme.typography.titleMedium)
            Text("حتى ننبهك إذا الوجبة قريبة من وقت الدواء.", fontSize = 13.sp, color = C.Muted)
            TextButton(onClick = {
                val m = vm.thyroidMedMinute ?: (7 * 60)
                TimePickerDialog(context, { _, h, min -> vm.thyroidMedMinute = h * 60 + min }, m / 60, m % 60, false).show()
            }) {
                Icon(Icons.Outlined.Schedule, contentDescription = null)
                Text(
                    vm.thyroidMedMinute?.let { Dates.time(it) } ?: "اختر الوقت",
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 16.sp,
                )
            }
        }
    }

    InfoNote("التطبيق يساعدك على أكل يناسب حالتك، بس ما يعوّض عن الطبيب أو الدواء.")
}

@Composable
private fun StepWeightGoal(vm: OnboardingViewModel) {
    StepHeader(3, 3, "شكد تريد تنزل؟")

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("وزنك الحالي", "${vm.weight} كغم", Modifier.weight(1f).height(72.dp))
        NumberField(
            "الوزن المستهدف", vm.targetWeight, { vm.targetWeight = it }, "كغم",
            Modifier.weight(1f), decimal = true, error = vm.targetError != null,
        )
    }
    vm.targetError?.let { Text(it, color = C.Red, fontSize = 13.sp) }

    val ideal = vm.idealWeight
    val range = vm.healthyRange
    val recommended = vm.recommendedTarget
    if (ideal != null && range != null && recommended != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(C.GreenSoft)
                .border(1.dp, Color(0xFFB9D6C7), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Favorite, contentDescription = null, tint = C.Green, modifier = Modifier.size(20.dp))
                Text("وزنك المثالي حسب طولك وعمرك", color = C.GreenDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${ideal.fmt()} كغم", color = C.GreenDark, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "الوزن الطبيعي لطولك من ${range.first.fmt()} إلى ${range.second.fmt()} كغم. " +
                    "تكدر تغيّر الهدف براحتك ضمن الحد الصحي.",
                color = C.GreenDark, fontSize = 13.sp, lineHeight = 20.sp,
            )
            if (vm.underweight) {
                Text("وزنك الحالي أقل من الطبيعي، فالأفضل تحافظ عليه أو تزيده بمراجعة الطبيب.", color = C.RedText, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip(
                    label = "الموصى به ${recommended.fmt()}",
                    selected = vm.targetWeight.toDoubleOrNull() == recommended,
                    onClick = { vm.targetWeight = recommended.fmt() },
                )
                vm.stagedTarget?.let { staged ->
                    TargetChip(
                        label = "هدف مرحلي ${staged.fmt()}",
                        selected = vm.targetWeight.toDoubleOrNull() == staged,
                        onClick = { vm.targetWeight = staged.fmt() },
                    )
                }
            }
            if (vm.stagedTarget != null) {
                Text(
                    "المسافة للوزن المثالي طويلة، فممكن تبدي بهدف مرحلي (نزول ١٠٪) وبعدين تكمل. نزول ٥–١٠٪ بس يحسّن الصحة بشكل واضح.",
                    color = C.GreenDark, fontSize = 12.sp, lineHeight = 19.sp,
                )
            }
        }
    }

    Text("سرعة النزول بالأسبوع (كغم)", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.25 to "مريح", 0.5 to "موصى", 0.75 to "أسرع", 1.0 to "الحد الأقصى").forEach { (rate, label) ->
            ChoiceBox(selected = vm.weeklyRate == rate, onClick = { vm.weeklyRate = rate }, modifier = Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(rate.fmt(2), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(label, fontSize = 11.sp, color = C.Muted)
                }
            }
        }
    }

    vm.planPreview?.let { plan ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(C.Ink)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("خطتك اليومية", color = C.OnDarkMuted, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${plan.dailyTarget}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("سعرة باليوم", color = C.OnDarkMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("العجز اليومي", "${plan.dailyDeficit} سعرة", Modifier.weight(1f), dark = true)
                StatTile(
                    "المدة التقريبية",
                    plan.weeksToGoal?.let { "${kotlin.math.ceil(it).toInt()} أسبوع" } ?: "—",
                    Modifier.weight(1f), dark = true,
                )
            }
            val eta = plan.etaEpochDay
            if (eta != null) {
                Text("توصل لـ ${vm.targetWeight} كغم حوالي ${Dates.fullDate(eta)}", color = Color.White, fontSize = 14.sp)
            } else {
                Text("هدفك تحافظ على وزنك الحالي، فسعراتك اليومية تساوي احتياجك.", color = Color.White, fontSize = 14.sp)
            }
        }
        if (plan.limitedByMinimum) {
            InfoNote("ما ننزل تحت ${plan.minimumCalories} سعرة باليوم حفاظاً على صحتك، فالمدة صارت أطول شوية من السرعة اللي اخترتها.")
        }
    }

    InfoNote("الحساب تقريبي. التطبيق ما ينزّل هدفك تحت حد أدنى آمن، وإذا عندك حالة صحية راجع طبيبك قبل أي نظام.")
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        label,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) C.Green else Color.White)
            .border(1.dp, C.Green, shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) Color.White else C.Green,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ChoiceBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (selected) C.GreenSoft else Color.White)
        .border(if (selected) 2.dp else 1.dp, if (selected) C.Green else C.Border, shape)
    val interactive = when (role) {
        Role.Checkbox -> base.toggleable(value = selected, role = role, onValueChange = { onClick() })
        Role.RadioButton -> base.selectable(selected = selected, role = role, onClick = onClick)
        else -> base.selectable(selected = selected, role = role, onClick = onClick)
    }
    Box(interactive) { content() }
}

