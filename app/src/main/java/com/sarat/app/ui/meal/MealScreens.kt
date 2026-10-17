package com.sarat.app.ui.meal

import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.data.gemini.FoodItem
import com.sarat.app.data.gemini.MealAnalysis
import com.sarat.app.data.gemini.Verdict
import com.sarat.app.ui.components.FitDot
import com.sarat.app.ui.components.PrimaryButton
import com.sarat.app.ui.components.SecondaryButton
import com.sarat.app.ui.components.VerdictCard
import com.sarat.app.ui.theme.C

// ───── شاشة الصورة ─────

@Composable
fun MealPhotoScreen(vm: MealViewModel, uri: Uri, onBack: () -> Unit, onSaved: () -> Unit) {
    LaunchedEffect(uri) { vm.startPhoto(uri) }

    Column(Modifier.fillMaxSize().background(C.Ground)) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2E3431))) {
            vm.preview?.let {
                Image(it, contentDescription = "صورة الوجبة", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            BackCircle(onBack, Modifier.statusBarsPadding().padding(16.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                vm.loading -> Loading("نحلل الصورة…")
                vm.error != null -> ErrorBox(vm.error!!, vm::retry)
                vm.analysis != null -> ResultContent(
                    analysis = vm.analysis!!,
                    goalsLabel = vm.goals.joinToString(" + ") { it.label },
                    remainingAfter = vm.remainingAfter,
                    onScale = vm::scaleItem,
                    onRemove = vm::removeItem,
                )
            }
        }
        SaveBar(vm, onSaved, secondary = "إعادة التحليل", onSecondary = vm::retry)
    }
}

// ───── شاشة الكتابة ─────

@Composable
fun TextEntryScreen(vm: MealViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Ground)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackCircle(onBack)
                Text("شنو أكلت؟", style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text("اكتب الوجبة بكلامك العادي، مع الكمية إذا تعرفها") },
                placeholder = { Text("مثلاً: صحن دولمة متوسط وكوب لبن") },
                minLines = 2,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::analyzeText,
                enabled = vm.description.isNotBlank() && !vm.loading,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                modifier = Modifier.align(Alignment.End).height(48.dp),
            ) { Text("احسب وقيّم", fontWeight = FontWeight.SemiBold) }

            when {
                vm.loading -> Loading("نحسب السعرات…")
                vm.error != null -> ErrorBox(vm.error!!, vm::retry)
                vm.analysis != null -> ResultContent(
                    analysis = vm.analysis!!,
                    goalsLabel = vm.goals.joinToString(" + ") { it.label },
                    remainingAfter = vm.remainingAfter,
                    onScale = vm::scaleItem,
                    onRemove = vm::removeItem,
                )
            }
        }
        if (vm.analysis != null) SaveBar(vm, onSaved, secondary = null, onSecondary = {})
    }
}

// ───── مكونات مشتركة ─────

@Composable
private fun ResultContent(
    analysis: MealAnalysis,
    goalsLabel: String,
    remainingAfter: Int,
    onScale: (Int, Double) -> Unit,
    onRemove: (Int) -> Unit,
) {
    if (!analysis.isFood || analysis.items.isEmpty()) {
        ErrorBox("ما لقينا أكل واضح. جرّب صورة أقرب أو اكتب الوجبة بالكلمات.", null)
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(analysis.mealName, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp))
            Text("تقدير تقريبي · عدّل الكميات إذا تحتاج", fontSize = 12.sp, color = C.Muted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${analysis.totalCalories}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("سعرة", fontSize = 12.sp, color = C.Muted)
        }
    }

    VerdictCard(Verdict.from(analysis.verdict), goalsLabel, analysis.verdictReason, analysis.suggestion)

    if (analysis.medicationNote.isNotBlank()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE6EBF5)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Medication, contentDescription = null, tint = Color(0xFF2B4C8C))
            Text(analysis.medicationNote, fontSize = 13.sp, color = Color(0xFF1F3766), lineHeight = 21.sp)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        analysis.items.forEachIndexed { i, item ->
            ItemRow(item, onMinus = { onScale(i, 0.75) }, onPlus = { onScale(i, 1.25) }, onRemove = { onRemove(i) })
        }
    }

    val over = remainingAfter < 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (over) C.OrangeSoft else C.GreenSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (over) C.OrangeText else C.GreenDark
        Text(if (over) "بعد هذه الوجبة تتجاوز هدفك بـ" else "بعد هذه الوجبة يبقى لك", fontSize = 14.sp, color = color)
        Text("${kotlin.math.abs(remainingAfter)} سعرة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ItemRow(item: FoodItem, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FitDot(item.fit)
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val portion = listOf(item.portion, if (item.grams > 0) "${item.grams} غم" else "")
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (portion.isNotBlank()) Text(portion, fontSize = 12.sp, color = C.Muted)
        }
        Text("${item.calories}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        SmallIconButton(Icons.Outlined.Remove, "تقليل ${item.name}", onMinus)
        SmallIconButton(Icons.Outlined.Add, "زيادة ${item.name}", onPlus)
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "حذف ${item.name}", tint = C.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(C.Ground)
            .border(1.dp, C.Border, RoundedCornerShape(12.dp)),
    ) { Icon(icon, contentDescription = label, tint = C.Ink) }
}

@Composable
private fun SaveBar(vm: MealViewModel, onSaved: () -> Unit, secondary: String?, onSecondary: () -> Unit) {
    val canSave = vm.analysis?.let { it.isFood && it.items.isNotEmpty() } == true && !vm.loading
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Ground)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (secondary != null) {
            SecondaryButton(secondary, onSecondary, Modifier.weight(1f), enabled = !vm.loading)
        }
        PrimaryButton("أضف للسجل", { vm.save(onSaved) }, Modifier.weight(2f), enabled = canSave)
    }
}

@Composable
private fun BackCircle(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White),
    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع", tint = C.Ink) }
}

@Composable
private fun Loading(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = C.Green)
        Text(text, color = C.Muted)
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.RedSoft).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, color = C.RedText, fontSize = 14.sp)
        if (onRetry != null) {
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = C.RedText)) { Text("حاول مرة ثانية") }
        }
    }
}
