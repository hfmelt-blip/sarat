package com.sarat.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sarat.app.MainActivity
import com.sarat.app.R
import com.sarat.app.SaratApp
import com.sarat.app.domain.Nutrition
import com.sarat.app.domain.Sex
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** تذكيرات يومية باللهجة: الصبح للفطور، المساء للسعرات الباقية، وقياس الوزن. */
object Reminders {
    const val CHANNEL = "reminders"
    const val EXTRA_OPEN = "open"
    const val OPEN_CAMERA = "camera"
    const val OPEN_PLAN = "plan"
    const val OPEN_PROGRESS = "progress"
    const val OPEN_HOME = "home"

    const val MORNING = "morning"
    const val EVENING = "evening"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "تذكيرات شد الهمة", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تذكير بالفطور والسعرات الباقية وقياس الوزن"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** [replace] = true بعد تغيير الأوقات، و false عند فتح التطبيق حتى ما تتصفر المواعيد. */
    fun scheduleAll(context: Context, replace: Boolean) {
        val prefs = (context.applicationContext as SaratApp).container.prefs
        if (!prefs.notificationsOn) {
            cancelAll(context)
            return
        }
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        schedule(context, MORNING, prefs.morningMinute, policy)
        schedule(context, EVENING, prefs.eveningMinute, policy)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MORNING)
        WorkManager.getInstance(context).cancelUniqueWork(EVENING)
    }

    fun schedule(context: Context, type: String, minuteOfDay: Int, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntil(minuteOfDay), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("type" to type))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(type, policy, request)
    }

    /** المدة لحد الموعد الجاي بهذا الوقت (اليوم أو باچر). */
    fun delayUntil(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        return java.time.Duration.between(now, next).toMillis()
    }

    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, id: Int, title: String, text: String, open: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN, open)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.success()
        val c = (applicationContext as SaratApp).container
        val prefs = c.prefs
        if (!prefs.notificationsOn) return Result.success()

        runCatching {
            val profile = c.repository.getProfile() ?: return@runCatching
            val today = Dates.todayEpoch()
            val meals = c.repository.mealsForDay(today).first()
            val weights = c.repository.weights.first()
            val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
            val plan = Nutrition.plan(profile, current, today)
            val remaining = plan.dailyTarget - meals.sumOf { it.calories }
            val she = profile.sex == Sex.FEMALE
            val name = profile.name.ifBlank { null }

            when (type) {
                Reminders.MORNING -> {
                    if (meals.isEmpty()) {
                        Reminders.notify(
                            applicationContext, 1,
                            "صباح الخير" + (name?.let { " يا $it" } ?: ""),
                            if (she) "صوّري فطورچ وخلّينا نبدي اليوم صح، يلا شدّي الهمة"
                            else "صوّر فطورك وخلّينا نبدي اليوم صح، يلا شد الهمة",
                            Reminders.OPEN_CAMERA,
                        )
                    }
                    val lastWeighIn = weights.lastOrNull()?.epochDay ?: profile.startEpochDay
                    if (prefs.weighReminderOn && Nutrition.isWeighInDue(lastWeighIn, today) &&
                        today - prefs.lastWeighReminderDay >= 3
                    ) {
                        prefs.lastWeighReminderDay = today
                        Reminders.notify(
                            applicationContext, 3,
                            "وقت تقيس وزنك",
                            "صار أسبوعين، قيس وزنك الصبح قبل الأكل حتى نحدّث خطتك وموعد وصولك",
                            Reminders.OPEN_PROGRESS,
                        )
                    }
                }
                Reminders.EVENING -> when {
                    meals.isEmpty() -> Reminders.notify(
                        applicationContext, 2,
                        "ما سجّلت أكل اليوم",
                        if (she) "سجّلي وجباتچ حتى نعرف شكد باقيلچ، ولو تقريبي" else "سجّل وجباتك حتى نعرف شكد باقيلك، ولو تقريبي",
                        Reminders.OPEN_HOME,
                    )
                    remaining > 150 -> Reminders.notify(
                        applicationContext, 2,
                        "باقيلك $remaining سعرة",
                        if (she) "شنو تاكلين؟ خلّينا نقترح عشاء من الموجود بالبيت" else "شنو تاكل؟ خلّينا نقترح عشاء من الموجود بالبيت",
                        Reminders.OPEN_PLAN,
                    )
                    remaining in 0..150 -> Reminders.notify(
                        applicationContext, 2,
                        if (she) "عاشت إيدچ!" else "عاشت إيدك!",
                        "وصلت لهدف اليوم تقريباً، كمّل هيچ",
                        Reminders.OPEN_HOME,
                    )
                    // إذا زاد عن هدفه ما نرسل شي، حتى ما يصير التطبيق مزعج أو يلوم
                    else -> Unit
                }
                else -> Unit
            }
            Unit
        }

        // موعد باچر
        val minute = if (type == Reminders.MORNING) prefs.morningMinute else prefs.eveningMinute
        Reminders.schedule(applicationContext, type, minute, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }
}
