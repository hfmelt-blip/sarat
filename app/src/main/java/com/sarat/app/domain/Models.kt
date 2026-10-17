package com.sarat.app.domain

enum class Sex(val label: String) { MALE("ذكر"), FEMALE("أنثى") }

enum class Activity(val factor: Double, val label: String, val hint: String) {
    SEDENTARY(1.2, "قليل الحركة", "عمل مكتبي، بدون رياضة"),
    LIGHT(1.375, "نشاط خفيف", "رياضة ١–٣ أيام بالأسبوع"),
    MODERATE(1.55, "نشاط متوسط", "رياضة ٣–٥ أيام بالأسبوع"),
    HIGH(1.725, "نشاط عالي", "رياضة يومية أو عمل بدني"),
}

/** نسبة الكاربوهيدرات من السعرات اليومية لكل هدف. */
enum class DietGoal(val label: String, val description: String, val carbShare: Double) {
    INSULIN_RESISTANCE(
        "مقاومة الإنسولين",
        "نقلل السكريات والنشويات المكررة، ونعطي أولوية للألياف والبروتين.",
        0.30,
    ),
    HYPOTHYROID(
        "خمول الغدة الدرقية",
        "أكل متوازن غني باليود والسيلينيوم، وتنبيه إذا الوجبة قريبة من وقت الدواء.",
        0.45,
    ),
    OBESITY_LIPIDS(
        "السمنة والدهون الثلاثية والحشوية",
        "نقلل السكر والدهون المشبعة والمقليات، ونزيد الخضار والأوميغا ٣.",
        0.40,
    ),
}

data class UserProfile(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val activity: Activity,
    val goals: Set<DietGoal>,
    val targetWeightKg: Double,
    val weeklyRateKg: Double,
    val startEpochDay: Long,
    /** تصحيح الاحتياج اليومي المتعلَّم من قياسات الوزن الفعلية (سعرة). */
    val tdeeAdjustment: Int = 0,
    /** وقت دواء الغدة بالدقائق من بداية اليوم، إن وُجد. */
    val thyroidMedMinute: Int? = null,
)

data class Plan(
    val tdee: Int,
    val dailyTarget: Int,
    val dailyDeficit: Int,
    val minimumCalories: Int,
    val weeklyLossKg: Double,
    val remainingKg: Double,
    val weeksToGoal: Double?,
    val etaEpochDay: Long?,
    /** صار الهدف محدوداً بالحد الأدنى الآمن، فالعجز أقل من المطلوب. */
    val limitedByMinimum: Boolean,
    val macros: Macros,
)

data class Macros(val proteinG: Int, val carbsG: Int, val fatG: Int)

enum class DayStatus { ON_TARGET, OVER, TOO_LOW, IN_PROGRESS, NO_DATA }

data class DayRecord(
    val epochDay: Long,
    val consumed: Int,
    val target: Int,
    val status: DayStatus,
    val message: String,
)

data class Calibration(
    val days: Int,
    val loggedDays: Int,
    val lostKg: Double,
    val expectedLostKg: Double,
    val averageIntake: Int,
    val actualDailyDeficit: Int,
    val newAdjustment: Int,
    /** الفرق بين الحرق الفعلي المستنتج والحرق المحسوب (سعرة/يوم). */
    val tdeeError: Int,
    /** هل البيانات كافية لتعديل الاحتياج؟ */
    val reliable: Boolean,
)
