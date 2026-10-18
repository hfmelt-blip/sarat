package com.sarat.app.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * كل الحسابات هنا تقريبية وتعتمد على معادلة Mifflin-St Jeor
 * وعلى أن الكيلوغرام من دهون الجسم ≈ 7700 سعرة.
 */
object Nutrition {
    const val KCAL_PER_KG = 7700.0
    const val MAX_WEEKLY_RATE_KG = 1.0
    const val MIN_HEALTHY_BMI = 18.5

    /** هامش مقبول فوق الهدف اليومي قبل اعتبار اليوم "زيادة". */
    const val OVER_TOLERANCE = 0.05

    /** هامش مقبول تحت الحد الأدنى: نقص بسيط بيوم واحد ما يضر، فيبقى اليوم ملتزم. */
    const val UNDER_TOLERANCE = 0.10

    /** أقل عدد أيام بين قياسين حتى نعدّل الاحتياج. */
    const val MIN_CALIBRATION_DAYS = 7

    /** أقل نسبة أيام مسجّلة بالفترة حتى نثق بمتوسط الاستهلاك. */
    const val MIN_LOGGED_SHARE = 0.7

    /** نأخذ نصف الفرق فقط بكل قياس حتى لا يقفز التقدير بسبب ماء الجسم. */
    const val CALIBRATION_DAMPING = 0.5
    const val MAX_ADJUSTMENT = 500

    const val WEIGH_IN_INTERVAL_DAYS = 14

    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    fun minimumCalories(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

    fun baseTdee(p: UserProfile, currentWeightKg: Double): Double =
        bmr(p.sex, currentWeightKg, p.heightCm, p.age) * p.activity.factor

    fun tdee(p: UserProfile, currentWeightKg: Double): Int =
        (baseTdee(p, currentWeightKg) + p.tdeeAdjustment).roundToInt()

    /**
     * الوزن المثالي التقريبي: مؤشر كتلة 22 للبالغين، و24 لمن عمره 65 فما فوق
     * (لأن الوزن الأعلى قليلاً أصح مع تقدم العمر). يُقرّب لأقرب نصف كيلو.
     */
    fun idealWeight(heightCm: Double, age: Int): Double {
        val m = heightCm / 100.0
        val bmi = if (age >= 65) 24.0 else 22.0
        return roundHalf(bmi * m * m)
    }

    /** المدى الطبيعي للوزن حسب الطول (مؤشر كتلة 18.5 إلى 24.9). */
    fun healthyRange(heightCm: Double): Pair<Double, Double> {
        val m = heightCm / 100.0
        return roundHalf(MIN_HEALTHY_BMI * m * m) to roundHalf(24.9 * m * m)
    }

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    /** هدف مرحلي واقعي: نزول 10٪ من الوزن الحالي. */
    fun stagedTarget(currentKg: Double): Double = roundHalf(currentKg * 0.9)

    private fun roundHalf(v: Double): Double = kotlin.math.round(v * 2) / 2.0

    fun minHealthyWeight(heightCm: Double): Double {
        val m = heightCm / 100.0
        return MIN_HEALTHY_BMI * m * m
    }

    fun plan(p: UserProfile, currentWeightKg: Double, todayEpochDay: Long): Plan {
        val tdee = tdee(p, currentWeightKg)
        val minCal = minimumCalories(p.sex)
        val remaining = max(0.0, currentWeightKg - p.targetWeightKg)
        val rate = p.weeklyRateKg.coerceIn(0.0, MAX_WEEKLY_RATE_KG)

        val requestedDeficit = if (remaining > 0) (rate * KCAL_PER_KG / 7.0).roundToInt() else 0
        val rawTarget = tdee - requestedDeficit
        val target = max(rawTarget, minCal)
        val deficit = max(0, tdee - target)
        val weeklyLoss = deficit * 7.0 / KCAL_PER_KG

        val weeks = if (remaining > 0 && weeklyLoss > 0) remaining / weeklyLoss else null
        val eta = weeks?.let { todayEpochDay + ceil(it * 7).toLong() }

        return Plan(
            tdee = tdee,
            dailyTarget = target,
            dailyDeficit = deficit,
            minimumCalories = minCal,
            weeklyLossKg = weeklyLoss,
            remainingKg = remaining,
            weeksToGoal = weeks,
            etaEpochDay = eta,
            limitedByMinimum = rawTarget < minCal,
            macros = macros(target, currentWeightKg, p.goals),
        )
    }

    fun macros(targetKcal: Int, weightKg: Double, goals: Set<DietGoal>): Macros {
        val carbShare = goals.minOfOrNull { it.carbShare } ?: 0.45
        // 1.6 غم بروتين لكل كغم، بحد أقصى 30٪ من السعرات
        val protein = min(1.6 * weightKg, targetKcal * 0.30 / 4.0)
        val carbs = targetKcal * carbShare / 4.0
        val fat = max(0.0, (targetKcal - protein * 4 - carbs * 4) / 9.0)
        return Macros(protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())
    }

    // ───── الالتزام اليومي ─────

    private val praise = listOf(
        "عاشت إيدك، ضمن هدفك",
        "يوم مثالي، كمّل هيج",
        "ضمن هدفك، استمر",
        "أحسنت! خطوة أقرب للهدف",
        "ممتاز، التزام يرفع الراس",
    )
    private val overMessages = listOf(
        "باچر يوم جديد",
        "ولا يهمك، نرجع للمسار",
        "صارت، المهم الاستمرار",
    )

    fun evaluateDay(
        epochDay: Long,
        consumed: Int,
        target: Int,
        minimum: Int,
        todayEpochDay: Long,
        streakAfter: Int = 0,
    ): DayRecord {
        val isToday = epochDay == todayEpochDay
        val limit = (target * (1 + OVER_TOLERANCE)).roundToInt()
        val floor = (minimum * (1 - UNDER_TOLERANCE)).roundToInt()
        val pick = abs(epochDay % 997).toInt()
        val status = when {
            consumed == 0 && !isToday -> DayStatus.NO_DATA
            consumed > limit -> DayStatus.OVER
            isToday -> if (consumed >= floor) DayStatus.ON_TARGET else DayStatus.IN_PROGRESS
            consumed < floor -> DayStatus.TOO_LOW
            else -> DayStatus.ON_TARGET
        }
        val message = when (status) {
            DayStatus.ON_TARGET ->
                if (consumed < minimum && !isToday) "ضمن هدفك، بس ناقص ${minimum - consumed} سعرة عن الحد الأدنى"
                else if (streakAfter >= 3) "ممتاز! $streakAfter أيام ورا بعض" else praise[pick % praise.size]
            DayStatus.OVER -> "زدت ${consumed - target} سعرة، ${overMessages[pick % overMessages.size]}"
            DayStatus.TOO_LOW -> "أكلت أقل من الحد الآمن، جسمك يحتاج أكثر"
            DayStatus.IN_PROGRESS -> "باقي ${max(0, target - consumed)} سعرة لليوم"
            DayStatus.NO_DATA -> "ما مسجّل أكل لهذا اليوم"
        }
        return DayRecord(epochDay, consumed, target, status, message)
    }

    /**
     * يبني سجل الأيام من [fromEpochDay] إلى [todayEpochDay] (الأحدث أولاً)
     * ويحسب سلسلة الالتزام لكل يوم.
     */
    fun buildHistory(
        totals: Map<Long, Int>,
        fromEpochDay: Long,
        todayEpochDay: Long,
        target: Int,
        minimum: Int,
    ): List<DayRecord> {
        if (todayEpochDay < fromEpochDay) return emptyList()
        val chronological = mutableListOf<DayRecord>()
        var streak = 0
        for (day in fromEpochDay..todayEpochDay) {
            val consumed = totals[day] ?: 0
            val first = evaluateDay(day, consumed, target, minimum, todayEpochDay)
            streak = when (first.status) {
                DayStatus.ON_TARGET -> streak + 1
                DayStatus.IN_PROGRESS -> streak
                else -> 0
            }
            chronological += if (first.status == DayStatus.ON_TARGET) {
                evaluateDay(day, consumed, target, minimum, todayEpochDay, streak)
            } else first
        }
        return chronological.reversed()
    }

    fun currentStreak(history: List<DayRecord>): Int {
        var streak = 0
        for (r in history) { // الأحدث أولاً
            when (r.status) {
                DayStatus.ON_TARGET -> streak++
                DayStatus.IN_PROGRESS -> continue
                else -> return streak
            }
        }
        return streak
    }

    // ───── إعادة المعايرة من قياس الوزن ─────

    /**
     * @param intakeByDay مجموع السعرات لكل يوم بين القياسين (بدون يوم القياس الجديد).
     */
    fun calibrate(
        p: UserProfile,
        previousWeightKg: Double,
        previousEpochDay: Long,
        newWeightKg: Double,
        newEpochDay: Long,
        intakeByDay: Map<Long, Int>,
    ): Calibration {
        val days = (newEpochDay - previousEpochDay).toInt()
        val logged = intakeByDay.filter { (d, kcal) -> d in previousEpochDay until newEpochDay && kcal > 0 }
        val avgIntake = if (logged.isEmpty()) 0 else logged.values.average().roundToInt()
        val lost = previousWeightKg - newWeightKg
        val midWeight = (previousWeightKg + newWeightKg) / 2
        val currentTdee = tdee(p, midWeight)

        val expected = if (days > 0 && avgIntake > 0) (currentTdee - avgIntake) * days / KCAL_PER_KG else 0.0
        val actualDeficit = if (days > 0) (lost * KCAL_PER_KG / days).roundToInt() else 0

        val reliable = days >= MIN_CALIBRATION_DAYS &&
            logged.size >= (days * MIN_LOGGED_SHARE) &&
            avgIntake >= minimumCalories(p.sex) * 0.6

        val error = if (avgIntake > 0) avgIntake + actualDeficit - currentTdee else 0
        val newAdjustment = if (reliable) {
            (p.tdeeAdjustment + error * CALIBRATION_DAMPING).roundToInt()
                .coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)
        } else p.tdeeAdjustment

        return Calibration(
            days = days,
            loggedDays = logged.size,
            lostKg = lost,
            expectedLostKg = expected,
            averageIntake = avgIntake,
            actualDailyDeficit = actualDeficit,
            newAdjustment = newAdjustment,
            tdeeError = error,
            reliable = reliable,
        )
    }

    fun isWeighInDue(lastWeighInEpochDay: Long, todayEpochDay: Long): Boolean =
        todayEpochDay - lastWeighInEpochDay >= WEIGH_IN_INTERVAL_DAYS
}
