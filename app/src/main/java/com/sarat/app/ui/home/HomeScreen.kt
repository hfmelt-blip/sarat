package com.sarat.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarat.app.data.Meal
import com.sarat.app.data.gemini.Verdict
import com.sarat.app.domain.Motivation
import com.sarat.app.ui.components.CalorieRing
import com.sarat.app.ui.components.MacroBar
import com.sarat.app.ui.components.MainBottomBar
import com.sarat.app.ui.components.SectionCard
import com.sarat.app.ui.components.Tab
import com.sarat.app.ui.components.VerdictBadge
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates
import com.sarat.app.util.AppSharer
import com.sarat.app.util.Images
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    hasKey: Boolean,
    onApiKey: () -> Unit,
    onTab: (Tab) -> Unit,
    onPhoto: (Uri) -> Unit,
    onText: () -> Unit,
    onWeighIn: () -> Unit,
    showWelcome: Boolean,
    onWelcomeShown: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // رقم عشوائي لكل فتحة للتطبيق حتى تتنوع عبارات التحفيز
    val seed = remember { kotlin.random.Random.nextInt(1000) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Meal?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onPhoto(Uri.parse(uri))
        pendingUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }

    Scaffold(
        containerColor = C.Ground,
        bottomBar = { MainBottomBar(Tab.TODAY, onTab) },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.longDay(s.today), fontSize = 13.sp, color = C.Muted, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { AppSharer.share(context) } }) {
                        Icon(Icons.Outlined.Share, contentDescription = "شارك التطبيق مع أصدقائك", tint = C.Green)
                    }
                    IconButton(onClick = onApiKey) {
                        Icon(Icons.Outlined.Key, contentDescription = "إعدادات Gemini", tint = C.Muted)
                    }
                }
            }

            item {
                val minute = Dates.nowMinute()
                WelcomeCard(
                    greeting = Motivation.greeting(s.profile.name, minute),
                    line = Motivation.line(motivationContext(s, minute), seed),
                    streak = s.streak,
                    minuteOfDay = minute,
                )
            }

            if (s.profile.name.isBlank()) {
                item { AddNameBanner { onTab(Tab.PROFILE) } }
            }

            if (s.profile.goals.isNotEmpty()) {
                item {
                    Text(
                        "هدفك: " + s.profile.goals.joinToString(" + ") { it.label },
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.GreenSoft)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        color = C.GreenDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                SectionCard(Modifier, background = Color.White) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CalorieRing(s.consumed, s.plan.dailyTarget)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Summary("الهدف", "${s.plan.dailyTarget}", Modifier.weight(1f))
                        Summary("المتناول", "${s.consumed}", Modifier.weight(1f))
                        Summary("العجز المخطط", "${s.plan.dailyDeficit}−", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MacroBar("بروتين", s.protein, s.plan.macros.proteinG, C.Green, Modifier.weight(1f))
                        MacroBar("كارب", s.carbs, s.plan.macros.carbsG, C.Orange, Modifier.weight(1f))
                        MacroBar("دهون", s.fat, s.plan.macros.fatG, C.Blue, Modifier.weight(1f))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val uri = Images.newCaptureUri(context)
                                pendingUri = uri.toString()
                                camera.launch(uri)
                            },
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                            Text("  صوّر وجبة", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onText,
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.5.dp, C.Ink),
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = null, tint = C.Ink)
                            Text("  اكتب وجبة", color = C.Ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  أو اختر صورة من المعرض", fontSize = 14.sp)
                    }
                }
            }

            if (!hasKey) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(C.OrangeSoft)
                            .clickable(onClick = onApiKey)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = C.OrangeText)
                        Column(Modifier.weight(1f)) {
                            Text("اربط Gemini حتى نحلل وجباتك", fontWeight = FontWeight.Bold, color = C.OrangeText, fontSize = 15.sp)
                            Text("مجاني وياخذ أقل من دقيقة", color = C.OrangeText, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (s.weighInDue) {
                item { WeighInBanner(onWeighIn) }
            }

            item { Text("وجبات اليوم", style = MaterialTheme.typography.titleMedium) }

            if (s.meals.isEmpty()) {
                item {
                    Text(
                        "ما سجلت وجبات لليوم بعد. صوّر وجبتك أو اكتبها حتى نحسب سعراتها.",
                        color = C.Muted, fontSize = 14.sp,
                    )
                }
            }
            items(s.meals, key = { it.id }) { meal -> MealRow(meal, onDelete = { toDelete = meal }) }
        }
    }

    val ws = state
    if (showWelcome && ws != null) {
        WelcomeOverlay(
            title = Motivation.welcomeTitle(ws.profile.name),
            line = Motivation.line(motivationContext(ws, Dates.nowMinute()), seed),
            onDone = onWelcomeShown,
        )
    }

    toDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف الوجبة؟") },
            text = { Text("راح تنحذف «${meal.title}» (${meal.calories} سعرة) من سجل اليوم.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMeal(meal.id); toDelete = null }) { Text("حذف", color = C.Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء") } },
        )
    }
}

private fun motivationContext(s: HomeState, minute: Int) = Motivation.Context(
    name = s.profile.name,
    sex = s.profile.sex,
    minuteOfDay = minute,
    consumed = s.consumed,
    target = s.plan.dailyTarget,
    streak = s.streak,
    mealsToday = s.meals.size,
)

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = C.Muted)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WeighInBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.GreenSoft)
            .border(1.dp, Color(0xFFB9D6C7), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.MonitorWeight, contentDescription = null, tint = C.GreenDark) }
        Column(Modifier.weight(1f)) {
            Text("صار أسبوعين، وقت تقيس وزنك", fontWeight = FontWeight.Bold, color = C.GreenDark, fontSize = 15.sp)
            Text("نقارن النزول الفعلي بالمخطط ونحدّث موعد الهدف", color = C.GreenDark, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = C.GreenDark)
    }
}

@Composable
private fun MealRow(meal: Meal, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (meal.source == "PHOTO") C.GreenSoft else C.Beige),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (meal.source == "PHOTO") Icons.Outlined.PhotoCamera else Icons.Outlined.EditNote,
                contentDescription = null,
                tint = if (meal.source == "PHOTO") C.Green else C.OrangeText,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(meal.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Dates.time(meal.minuteOfDay) + if (meal.source == "PHOTO") " · بالكاميرا" else " · بالكتابة",
                fontSize = 12.sp, color = C.Muted,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${meal.calories}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            VerdictBadge(Verdict.from(meal.verdict))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف ${meal.title}", tint = C.Muted)
        }
    }
}
