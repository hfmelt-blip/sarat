package com.sarat.app.data

import android.content.Context

/** إعدادات صغيرة تنحفظ على الموبايل: آخر يوم طلعت بيه شاشة الترحيب، وآخر احتفال. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var lastWelcomeDay: Long
        get() = prefs.getLong("last_welcome_day", -1)
        set(value) = prefs.edit().putLong("last_welcome_day", value).apply()

    var lastCelebratedStreak: Int
        get() = prefs.getInt("last_celebrated_streak", 0)
        set(value) = prefs.edit().putInt("last_celebrated_streak", value).apply()

    /** المكونات المتوفرة بالبيت، تنحفظ حتى ما يكتبها كل مرة. */
    var pantry: String
        get() = prefs.getString("pantry", "").orEmpty()
        set(value) = prefs.edit().putString("pantry", value).apply()

    var mealsPerDay: Int
        get() = prefs.getInt("meals_per_day", 3)
        set(value) = prefs.edit().putInt("meals_per_day", value).apply()

    // ───── الإشعارات ─────
    var notificationsOn: Boolean
        get() = prefs.getBoolean("notif_on", true)
        set(value) = prefs.edit().putBoolean("notif_on", value).apply()

    var morningMinute: Int
        get() = prefs.getInt("notif_morning", 9 * 60)
        set(value) = prefs.edit().putInt("notif_morning", value).apply()

    var eveningMinute: Int
        get() = prefs.getInt("notif_evening", 19 * 60)
        set(value) = prefs.edit().putInt("notif_evening", value).apply()

    var weighReminderOn: Boolean
        get() = prefs.getBoolean("notif_weigh", true)
        set(value) = prefs.edit().putBoolean("notif_weigh", value).apply()

    var lastWeighReminderDay: Long
        get() = prefs.getLong("notif_weigh_last", -1)
        set(value) = prefs.edit().putLong("notif_weigh_last", value).apply()

    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean("notif_asked", false)
        set(value) = prefs.edit().putBoolean("notif_asked", value).apply()
}
