package com.sarat.app.ui.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarat.app.data.gemini.MealSuggestion
import com.sarat.app.data.gemini.Verdict
import com.sarat.app.ui.components.PrimaryButton
import com.sarat.app.ui.components.VerdictBadge
import com.sarat.app.ui.theme.C
import com.sarat.app.util.TextShare
import kotlinx.coroutines.delay

// ───── مكونات مشتركة ─────

@Composable
fun KitchenHeader(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(C.Card)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع", tint = C.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = C.Ink)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = C.Muted)
        }
        actions()
    }
}

@Composable
fun RecipeCard(title: String, kcal: Int, prepMinutes: Int, tag: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.GreenSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = C.Green)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink)
            Text(
                listOfNotNull(tag, if (prepMinutes > 0) "$prepMinutes دقيقة" else null).joinToString(" · ").ifBlank { " " },
                fontSize = 12.sp, color = C.Muted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$kcal", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.Green)
            Text("سعرة", fontSize = 11.sp, color = C.Muted)
        }
    }
}

@Composable
fun PortionRow(portion: Double, onChoose: (Double) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0.5 to "نص", 1.0 to "حصة", 1.5 to "حصة ونص", 2.0 to "حصتين", 4.0 to "٤ حصص").forEach { (p, label) ->
            val selected = portion == p
            val shape = RoundedCornerShape(16.dp)
            Text(
                label,
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) C.Green else C.Card)
                    .border(1.dp, if (selected) C.Green else C.Border, shape)
                    .selectable(selected = selected, role = Role.RadioButton) { onChoose(p) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                color = if (selected) Color.White else C.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** محتوى الوصفة للقراءة: الملخص، المكونات، والخطوات. */
@Composable
fun RecipeBody(s: MealSuggestion) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        Text(
            "بروتين ${s.totalProtein.toInt()}غ · كارب ${s.totalCarbs.toInt()}غ · دهون ${s.totalFat.toInt()}غ",
            fontSize = 13.sp, color = C.Ink,
        )
        if (s.prepMinutes > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = C.Muted, modifier = Modifier.size(16.dp))
                Text("التحضير حوالي ${s.prepMinutes} دقيقة", fontSize = 13.sp, color = C.Muted)
            }
        }
    }

    Text("المقادير", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.ingredients.forEach { i ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card)
                    .border(1.dp, if (i.available) C.Line else C.WarnBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(i.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.Ink)
                    val amount = listOf(i.amount, if (i.grams > 0) "${i.grams} غم" else "").filter { it.isNotBlank() }.joinToString(" · ")
                    if (amount.isNotBlank()) Text(amount, fontSize = 12.sp, color = C.Muted)
                    if (!i.available) Text("تحتاج تشتريه", fontSize = 12.sp, color = C.OrangeText)
                }
                Text("${i.calories}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.Ink)
            }
        }
    }

    if (s.steps.isNotEmpty()) {
        Text("طريقة التحضير", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.steps.forEachIndexed { n, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(C.GreenSoft), contentAlignment = Alignment.Center) {
                        Text("${n + 1}", color = C.GreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(step, fontSize = 14.sp, lineHeight = 22.sp, color = C.Ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ───── الوصفات المحفوظة ─────

@Composable
fun RecipesScreen(vm: RecipesViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().background(C.Ground).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { KitchenHeader("وصفاتي", "تنفتح بدون إنترنت وبدون Gemini", onBack) }
        if (saved.isEmpty()) {
            item {
                Text(
                    "ما عندك وصفات محفوظة بعد. من «شنو آكل؟» اضغط «احفظ الوصفة» على أي اقتراح يعجبك.",
                    color = C.Muted, fontSize = 14.sp, lineHeight = 22.sp,
                )
            }
        }
        items(saved, key = { it.id }) { r ->
            RecipeCard(r.recipe.dishName, r.recipe.totalCalories, r.recipe.prepMinutes, null) { onOpen(r.id) }
        }
    }
}

@Composable
fun RecipeDetailScreen(vm: RecipeDetailViewModel, onBack: () -> Unit, onEaten: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val s = vm.scaled
    LaunchedEffect(vm.message) {
        if (vm.message != null) { delay(2500); vm.clearMessage() }
    }
    Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KitchenHeader("الوصفة", null, onBack) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الوصفة", tint = C.Muted)
                }
            }
            if (s == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Text("الكمية", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            PortionRow(vm.portion, vm::choosePortion)
            RecipeBody(s)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { TextShare.share(context, TextShare.recipeText(s)) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = C.Ink, modifier = Modifier.size(18.dp))
                    Text("  شارك بواتساب", color = C.Ink, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { if (s.ingredients.any { !it.available }) vm.addMissingToShopping() else vm.addAllToShopping() },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = C.Ink, modifier = Modifier.size(18.dp))
                    Text(
                        if (s.ingredients.any { !it.available }) "  الناقص للمشتريات" else "  للمشتريات",
                        color = C.Ink, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            vm.message?.let { Text(it, color = C.GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
        if (s != null) {
            Box(Modifier.fillMaxWidth().background(C.Ground).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton("أكلتها، أضفها للسجل", { vm.eat(onEaten) })
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الوصفة؟") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("حذف", color = C.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}

// ───── قائمة المشتريات ─────

@Composable
fun ShoppingScreen(vm: ShoppingViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val open = items.count { !it.done }
    LazyColumn(
        Modifier.fillMaxSize().background(C.Ground).statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            KitchenHeader("قائمة المشتريات", if (open > 0) "$open أشياء تحتاج تشتريها" else null, onBack) {
                if (open > 0) {
                    IconButton(onClick = { TextShare.share(context, TextShare.shoppingText(items)) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "شارك القائمة", tint = C.Green)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.input, onValueChange = { vm.input = it.take(80) },
                    placeholder = { Text("أضف شي (مثلاً: طماطة، خيار)") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = vm::add, enabled = vm.input.isNotBlank(),
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.Green),
                ) { Icon(Icons.Outlined.Add, contentDescription = "أضف", tint = Color.White) }
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "القائمة فاضية. المكونات الناقصة من «شنو آكل؟» تنضاف هنا بضغطة.",
                    color = C.Muted, fontSize = 14.sp, lineHeight = 22.sp,
                )
            }
        }
        items(items, key = { it.id }) { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)
                    .border(1.dp, C.Line, RoundedCornerShape(14.dp))
                    .clickable { vm.toggle(item) }
                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.done, onCheckedChange = { vm.toggle(item) }, colors = CheckboxDefaults.colors(checkedColor = C.Green))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (item.done) C.Muted else C.Ink,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                    if (item.amount.isNotBlank()) Text(item.amount, fontSize = 12.sp, color = C.Muted)
                }
                IconButton(onClick = { vm.delete(item) }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف ${item.name}", tint = C.Muted)
                }
            }
        }
        if (items.any { it.done }) {
            item {
                TextButton(onClick = vm::clearBought) { Text("امسح اللي اشتريته", color = C.Muted) }
            }
        }
    }
}
