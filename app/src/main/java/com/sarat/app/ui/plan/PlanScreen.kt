package com.sarat.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.data.gemini.MealSuggestion
import com.sarat.app.data.gemini.PlanIngredient
import com.sarat.app.data.gemini.Verdict
import com.sarat.app.domain.MealBudget
import com.sarat.app.domain.MealPlanner
import com.sarat.app.ui.components.MealResultSkeleton
import com.sarat.app.ui.components.PrimaryButton
import com.sarat.app.ui.components.VerdictBadge
import com.sarat.app.ui.theme.C

/** مكونات شائعة بالبيت العراقي، تنضاف بضغطة. */
private val quickItems = listOf(
    "بيض", "خبز", "صمون", "تمن", "برغل", "دجاج", "لحم", "تونة", "عدس", "فاصوليا", "حمص",
    "طماطة", "خيار", "بصل", "بطاطا", "باذنجان", "شجر", "خس", "لبن", "جبن", "قيمر", "شوفان", "تمر",
)

@Composable
fun PlanScreen(
    vm: PlanViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRecipes: () -> Unit,
    onShopping: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Ground)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(C.Card)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع", tint = C.Ink)
                }
                Column(Modifier.weight(1f)) {
                    Text("شنو آكل؟", style = MaterialTheme.typography.headlineSmall, color = C.Ink)
                    Text("وجبة من الموجود وبحدود سعراتك", fontSize = 13.sp, color = C.Muted)
                }
                IconButton(onClick = onRecipes) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "وصفاتي", tint = C.Green)
                }
                IconButton(onClick = onShopping) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = "قائمة المشتريات", tint = C.Green)
                }
            }

            // عدد الوجبات ونوع الوجبة
            Text("عدد وجباتك باليوم", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { n ->
                    Pill("$n وجبات", vm.mealsPerDay == n) { vm.chooseMealsPerDay(n) }
                }
            }
            Text("الوجبة", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealPlanner.slots(vm.mealsPerDay).forEach { s ->
                    Pill(s.label, vm.slot == s) { vm.chooseSlot(s) }
                }
            }

            vm.budget?.let { BudgetCard(it) }

            // المكونات
            OutlinedTextField(
                value = vm.pantry,
                onValueChange = vm::onPantry,
                label = { Text("شنو موجود عندك بالبيت؟") },
                placeholder = { Text("مثلاً: بيض، طماطة، خبز أسمر، جبن، خيار") },
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickItems.forEach { item -> SmallChip("+ $item") { vm.addToPantry(item) } }
            }
            Text("المكونات تنحفظ، فالمرة الجاية بس عدّل عليها.", fontSize = 12.sp, color = C.Muted)

            OutlinedTextField(
                value = vm.wish,
                onValueChange = { vm.wish = it },
                label = { Text("تريد أكلة معينة؟ (اختياري)") },
                placeholder = { Text("مثلاً: عجة، شوربة عدس، مقلوبة") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { vm.suggest() },
                enabled = vm.pantry.isNotBlank() && !vm.loading && vm.budget != null,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Outlined.Restaurant, contentDescription = null)
                Text("  اقترح لي وجبة", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = vm::suggestOffline,
                enabled = !vm.loading && vm.budget != null,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = C.Green, modifier = Modifier.size(18.dp))
                Text("  اقترح من الوصفات المحفوظة (بدون إنترنت)", color = C.Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            when {
                vm.loading -> MealResultSkeleton("نجهّز لك وجبة من الموجود…")
                vm.error != null -> Text(vm.error!!, color = C.RedText, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.RedSoft).padding(14.dp))
                vm.suggestion != null -> SuggestionContent(vm, vm.suggestion!!)
            }
        }

        val s = vm.suggestion
        if (s != null && s.possible && s.ingredients.isNotEmpty() && !vm.loading) {
            Box(Modifier.fillMaxWidth().background(C.Ground).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton("أكلتها، أضفها للسجل", { vm.eat(onSaved) })
            }
        }
    }
}

@Composable
private fun BudgetCard(b: MealBudget) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Hero)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("سعرات وجبة ال${b.slot.label}", color = C.OnDarkMuted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${b.kcal}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("سعرة تقريباً", color = C.OnDarkMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            "بروتين ${b.proteinG}غ · كارب ${b.carbsG}غ · دهون ${b.fatG}غ",
            color = Color.White, fontSize = 13.sp,
        )
        Text(
            "باقيلك اليوم ${b.remainingBefore.coerceAtLeast(0)} سعرة" +
                if (b.later.isNotEmpty()) " · بعدها: " + b.later.joinToString("، ") { "${it.first.label} ~${it.second}" } else "",
            color = C.OnDarkMuted, fontSize = 13.sp, lineHeight = 20.sp,
        )
        if (b.nearlyDone) {
            Text("سعرات اليوم قربت تخلص، فنقترح شي خفيف جداً.", color = Color(0xFFFFD27A), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SuggestionContent(vm: PlanViewModel, s: MealSuggestion) {
    if (!s.possible) {
        Text(
            s.note.ifBlank { "ما نكدر نسوي وجبة مناسبة من هذي المكونات. ضيف شي ثاني وجرب." },
            color = C.OrangeText, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.OrangeSoft).padding(14.dp),
        )
        return
    }
    val budget = vm.budget
    if (vm.offline) {
        Text(
            "من الوصفات المحفوظة، بدون إنترنت",
            color = C.GreenDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.GreenSoft).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.dishName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                VerdictBadge(Verdict.from(s.verdict))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.totalCalories}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Green)
                Text("سعرة", fontSize = 12.sp, color = C.Muted)
            }
        }
        if (s.summary.isNotBlank()) Text(s.summary, fontSize = 14.sp, color = C.Muted, lineHeight = 22.sp)
        if (budget != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.sarat.app.domain.MacroCheck.lines(s, budget).forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.label, fontSize = 13.sp, color = C.Muted, modifier = Modifier.weight(1f))
                        Text(
                            "${l.value} من ${l.target}غ" + if (l.ok) "  ✓" else "  · ${l.hint}",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (l.ok) C.GreenDark else C.OrangeText,
                        )
                    }
                }
            }
        } else Text(
            "بروتين ${s.totalProtein.toInt()}غ · كارب ${s.totalCarbs.toInt()}غ · دهون ${s.totalFat.toInt()}غ",
            fontSize = 13.sp, color = C.Ink,
        )
        if (s.prepMinutes > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = C.Muted, modifier = Modifier.size(16.dp))
                Text("التحضير حوالي ${s.prepMinutes} دقيقة", fontSize = 13.sp, color = C.Muted)
            }
        }
        if (s.note.isNotBlank()) Text(s.note, fontSize = 13.sp, color = C.OrangeText, lineHeight = 20.sp)
    }

    Text("المكونات والكميات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.ingredients.forEachIndexed { i, item ->
            IngredientRow(
                item,
                onMinus = { vm.scaleIngredient(i, 0.75) },
                onPlus = { vm.scaleIngredient(i, 1.25) },
                onRemove = { vm.removeIngredient(i) },
                onShop = { vm.addToShopping(i) },
            )
        }
    }

    if (s.steps.isNotEmpty()) {
        Text("طريقة التحضير", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(C.GreenSoft), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = C.GreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(step, fontSize = 14.sp, lineHeight = 22.sp, color = C.Ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (budget != null) {
        val after = budget.remainingBefore - s.totalCalories
        val over = after < 0
        Text(
            if (over) "بعد هذي الوجبة تتجاوز هدف اليوم بـ ${-after} سعرة"
            else "بعد هذي الوجبة يبقى لك $after سعرة" +
                if (budget.later.isNotEmpty()) " لـ " + budget.later.joinToString(" و") { it.first.label } else " لباقي اليوم",
            color = if (over) C.OrangeText else C.GreenDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (over) C.OrangeSoft else C.GreenSoft).padding(12.dp),
        )
    }

    // حفظ ومشاركة ومشتريات
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = vm::saveRecipe, enabled = !vm.savedCurrent,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Icon(
                if (vm.savedCurrent) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                contentDescription = null, tint = C.Green, modifier = Modifier.size(18.dp),
            )
            Text(if (vm.savedCurrent) "  محفوظة" else "  احفظ الوصفة", color = C.Green, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = { com.sarat.app.util.TextShare.share(context, com.sarat.app.util.TextShare.recipeText(s)) },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null, tint = C.Ink, modifier = Modifier.size(18.dp))
            Text("  شارك بواتساب", color = C.Ink, fontWeight = FontWeight.SemiBold)
        }
    }
    if (s.ingredients.any { !it.available }) {
        OutlinedButton(
            onClick = vm::addMissingToShopping,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = C.OrangeText, modifier = Modifier.size(18.dp))
            Text("  أضف الناقص لقائمة المشتريات", color = C.OrangeText, fontWeight = FontWeight.SemiBold)
        }
    }
    vm.notice?.let { msg ->
        androidx.compose.runtime.LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.notice = null }
        Text(msg, color = C.GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }

    // التعديل
    OutlinedTextField(
        value = vm.change,
        onValueChange = { vm.change = it },
        label = { Text("تريد تعدّل عليها؟") },
        placeholder = { Text("مثلاً: بدون بصل، زيد البروتين، خليها أسرع") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = vm::applyChange,
            enabled = vm.change.isNotBlank() && !vm.loading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text("عدّل الوجبة", color = C.Ink, fontWeight = FontWeight.SemiBold) }
        OutlinedButton(
            onClick = { vm.suggest(another = true) },
            enabled = !vm.loading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = C.Ink, modifier = Modifier.size(18.dp))
            Text("  اقترح غيرها", color = C.Ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IngredientRow(
    item: PlanIngredient,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onRemove: () -> Unit,
    onShop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.Card)
            .border(1.dp, if (item.available) C.Line else C.WarnBorder, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink)
            val portion = listOf(item.amount, if (item.grams > 0) "${item.grams} غم" else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (portion.isNotBlank()) Text(portion, fontSize = 12.sp, color = C.Muted)
            if (!item.available) {
                Row(Modifier.clickable(onClick = onShop), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = C.OrangeText, modifier = Modifier.size(14.dp))
                    Text("تحتاج تشتريه · أضف للمشتريات", fontSize = 12.sp, color = C.OrangeText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("${item.calories}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink)
        SmallIcon(Icons.Outlined.Remove, "تقليل ${item.name}", onMinus)
        SmallIcon(Icons.Outlined.Add, "زيادة ${item.name}", onPlus)
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "حذف ${item.name}", tint = C.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Ground)
            .border(1.dp, C.Border, RoundedCornerShape(12.dp)),
    ) { Icon(icon, contentDescription = label, tint = C.Ink) }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) C.Green else C.Card)
            .border(1.dp, if (selected) C.Green else C.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (selected) Color.White else C.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text,
        modifier = Modifier
            .clip(shape)
            .background(C.Card)
            .border(1.dp, C.Border, shape)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = C.Ink,
        fontSize = 13.sp,
    )
}
