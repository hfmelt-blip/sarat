package com.sarat.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.sarat.app.MainActivity
import com.sarat.app.SaratApp
import com.sarat.app.domain.Nutrition
import com.sarat.app.notify.Reminders
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.first

private data class WidgetData(val ready: Boolean, val remaining: Int, val consumed: Int, val target: Int)

/** ودجت الشاشة الرئيسية: السعرات الباقية لليوم، وزر للتصوير وزر لـ «شنو آكل؟». */
class SaratWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val repo = (context.applicationContext as SaratApp).container.repository
        val profile = repo.getProfile() ?: return WidgetData(false, 0, 0, 0)
        val today = Dates.todayEpoch()
        val weight = repo.weights.first().lastOrNull()?.kg ?: profile.startWeightKg
        val target = Nutrition.plan(profile, weight, today).dailyTarget
        val consumed = repo.mealsForDay(today).first().sumOf { it.calories }
        return WidgetData(true, target - consumed, consumed, target)
    }

    private fun open(context: Context, action: String) =
        actionStartActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(Reminders.EXTRA_OPEN, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val bg = ColorProvider(day = Color(0xFF1F6F54), night = Color(0xFF1C211F))
        val white = ColorProvider(day = Color.White, night = Color.White)
        val soft = ColorProvider(day = Color(0xFFD5EADF), night = Color(0xFFA3ABA6))
        val chip = ColorProvider(day = Color(0x33FFFFFF), night = Color(0xFF2F8F6C))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(open(context, Reminders.OPEN_HOME)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!d.ready) {
                Text("افتح «شد الهمة» وكمّل معلوماتك", style = TextStyle(color = white, fontSize = 14.sp, textAlign = TextAlign.Center))
                return@Column
            }
            val over = d.remaining < 0
            Text(
                if (over) "زيادة اليوم" else "باقيلك اليوم",
                style = TextStyle(color = soft, fontSize = 13.sp, textAlign = TextAlign.Center),
            )
            Text(
                "${kotlin.math.abs(d.remaining)} سعرة",
                style = TextStyle(color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            Text(
                "أكلت ${d.consumed} من ${d.target}",
                style = TextStyle(color = soft, fontSize = 12.sp, textAlign = TextAlign.Center),
            )
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier.background(chip).cornerRadius(14.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clickable(open(context, Reminders.OPEN_CAMERA)),
                ) { Text("صوّر وجبة", style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier.background(chip).cornerRadius(14.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clickable(open(context, Reminders.OPEN_PLAN)),
                ) { Text("شنو آكل؟", style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
            }
        }
    }
}

class SaratWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SaratWidget()
}
