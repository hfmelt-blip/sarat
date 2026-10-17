package com.sarat.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

enum class MealSlot(val label: String, val english: String) {
    BREAKFAST("فطور", "breakfast"),
    LUNCH("غداء", "lunch"),
    SNACK("سناك", "snack"),
    DINNER("عشاء", "dinner"),
}

data class MealBudget(
    val slot: MealSlot,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** السعرات الباقية لليوم قبل هذي الوجبة. */
    val remainingBefore: Int,
    /** الوجبات الجاية باليوم وتقدير سعرات كل وحدة. */
    val later: List<Pair<MealSlot, Int>>,
    /** خلصت سعرات اليوم تقريباً، فالاقتراح يكون خفيف جداً. */
    val nearlyDone: Boolean,
)

/** يوزّع السعرات الباقية على الوجبات الباقية حسب الوقت وعدد الوجبات. */
object MealPlanner {

    const val LIGHT_MEAL_KCAL = 150

    fun slots(mealsPerDay: Int): List<MealSlot> = when (mealsPerDay) {
        2 -> listOf(MealSlot.BREAKFAST, MealSlot.DINNER)
        4 -> listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.SNACK, MealSlot.DINNER)
        else -> listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)
    }

    /** وزن كل وجبة من سعرات اليوم. */
    fun weight(slot: MealSlot, mealsPerDay: Int): Double = when (mealsPerDay) {
        2 -> if (slot == MealSlot.BREAKFAST) 0.45 else 0.55
        4 -> when (slot) {
            MealSlot.BREAKFAST -> 0.25
            MealSlot.LUNCH -> 0.35
            MealSlot.SNACK -> 0.10
            MealSlot.DINNER -> 0.30
        }
        else -> when (slot) {
            MealSlot.BREAKFAST -> 0.25
            MealSlot.LUNCH -> 0.40
            else -> 0.35
        }
    }

    /** الوجبة المناسبة للوقت الحالي. */
    fun slotForTime(minuteOfDay: Int, mealsPerDay: Int): MealSlot {
        val h = minuteOfDay / 60
        return when (mealsPerDay) {
            2 -> if (h in 4..14) MealSlot.BREAKFAST else MealSlot.DINNER
            4 -> when (h) {
                in 4..10 -> MealSlot.BREAKFAST
                in 11..15 -> MealSlot.LUNCH
                in 16..18 -> MealSlot.SNACK
                else -> MealSlot.DINNER
            }
            else -> when (h) {
                in 4..10 -> MealSlot.BREAKFAST
                in 11..15 -> MealSlot.LUNCH
                else -> MealSlot.DINNER
            }
        }
    }

    fun budget(
        slot: MealSlot,
        mealsPerDay: Int,
        target: Int,
        consumed: Int,
        macros: Macros,
        consumedProtein: Double,
        consumedCarbs: Double,
        consumedFat: Double,
    ): MealBudget {
        val all = slots(mealsPerDay)
        val start = all.indexOf(slot).let { if (it < 0) 0 else it }
        val rest = all.drop(start)
        val totalWeight = rest.sumOf { weight(it, mealsPerDay) }
        val remaining = target - consumed
        val share = weight(slot, mealsPerDay) / totalWeight

        val nearlyDone = remaining < LIGHT_MEAL_KCAL
        val kcal = if (nearlyDone) LIGHT_MEAL_KCAL else (remaining * share).roundToInt()
        val ratio = if (nearlyDone) LIGHT_MEAL_KCAL.toDouble() / max(target, 1) else share

        fun left(t: Int, c: Double) = max(0.0, t - c)
        val later = rest.drop(1).map { s ->
            s to if (nearlyDone) 0 else (remaining * weight(s, mealsPerDay) / totalWeight).roundToInt()
        }
        return MealBudget(
            slot = slot,
            kcal = kcal,
            proteinG = (left(macros.proteinG, consumedProtein) * ratio).roundToInt().coerceAtLeast(10),
            carbsG = (left(macros.carbsG, consumedCarbs) * ratio).roundToInt(),
            fatG = (left(macros.fatG, consumedFat) * ratio).roundToInt(),
            remainingBefore = remaining,
            later = later,
            nearlyDone = nearlyDone,
        )
    }
}
