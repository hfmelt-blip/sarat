package com.sarat.app.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarat.app.data.AppPrefs
import com.sarat.app.notify.Reminders
import com.sarat.app.ui.components.InfoNote
import com.sarat.app.ui.theme.C
import com.sarat.app.util.Dates

/** يطلب إذن الإشعارات مرة وحدة (أندرويد ١٣ فما فوق). */
@Composable
fun NotificationPermissionGate(prefs: AppPrefs) {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (prefs.notificationsOn && !prefs.askedNotificationPermission) {
            prefs.askedNotificationPermission = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun SettingsScreen(prefs: AppPrefs, onApiKey: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(prefs.notificationsOn) }
    var morning by remember { mutableIntStateOf(prefs.morningMinute) }
    var evening by remember { mutableIntStateOf(prefs.eveningMinute) }
    var weigh by remember { mutableStateOf(prefs.weighReminderOn) }
    var allowed by remember { mutableStateOf(Reminders.canNotify(context)) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && Reminders.canNotify(context)
    }

    fun apply() {
        prefs.notificationsOn = on
        prefs.morningMinute = morning
        prefs.eveningMinute = evening
        prefs.weighReminderOn = weigh
        Reminders.scheduleAll(context, replace = true)
    }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, false).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(C.Card)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع", tint = C.Ink)
            }
            Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, color = C.Ink)
        }

        Text("الإشعارات", style = MaterialTheme.typography.titleMedium, color = C.Ink)
        Group {
            SwitchRow(Icons.Outlined.Notifications, "تذكيرات يومية", "باللهجة وبدون إزعاج", on) {
                on = it
                apply()
                if (it && !allowed && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (on) {
                ClickRow(Icons.Outlined.WbSunny, "تذكير الفطور", "يطلع إذا ما سجّلت فطور · ${Dates.time(morning)}") {
                    pickTime(morning) { morning = it; apply() }
                }
                ClickRow(Icons.Outlined.NightsStay, "تذكير المساء", "شكد باقيلك ومقترح عشاء · ${Dates.time(evening)}") {
                    pickTime(evening) { evening = it; apply() }
                }
                SwitchRow(Icons.Outlined.MonitorWeight, "تذكير قياس الوزن", "كل أسبوعين", weigh) {
                    weigh = it
                    apply()
                }
            }
        }
        if (on && !allowed) {
            InfoNote("الإشعارات مطفية من إعدادات الموبايل. فعّلها لتطبيق «شد الهمة» من الإعدادات، ثم الإشعارات.")
        }

        Text("الذكاء الاصطناعي", style = MaterialTheme.typography.titleMedium, color = C.Ink)
        Group {
            ClickRow(Icons.Outlined.Key, "مفتاح Gemini", "تغيير المفتاح المجاني", onApiKey)
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(18.dp)),
    ) { content() }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = C.Green)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink)
            Text(subtitle, fontSize = 12.sp, color = C.Muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Green),
        )
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = C.Green)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.Ink)
            Text(subtitle, fontSize = 12.sp, color = C.Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = C.Muted)
    }
}
