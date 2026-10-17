package com.sarat.app.data.gemini

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class FoodItem(
    val name: String,
    val portion: String = "",
    val grams: Int = 0,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    /** GOOD | MODERATE | AVOID — مدى مناسبة المكوّن لأهداف المستخدم. */
    val fit: String = "MODERATE",
) {
    /** يكبّر أو يصغّر الكمية ويحدّث القيم بنفس النسبة. */
    fun scaled(factor: Double): FoodItem = copy(
        grams = (grams * factor).roundToInt(),
        calories = (calories * factor).roundToInt(),
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )
}

@Serializable
data class MealAnalysis(
    val isFood: Boolean = true,
    val mealName: String = "",
    val items: List<FoodItem> = emptyList(),
    /** GOOD | OK_WITH_CHANGES | NOT_SUITABLE */
    val verdict: String = "OK_WITH_CHANGES",
    val verdictReason: String = "",
    val suggestion: String = "",
    /** ملاحظة خاصة بدواء الغدة الدرقية إن وُجدت. */
    val medicationNote: String = "",
) {
    val totalCalories: Int get() = items.sumOf { it.calories }
    val totalProtein: Double get() = items.sumOf { it.proteinG }
    val totalCarbs: Double get() = items.sumOf { it.carbsG }
    val totalFat: Double get() = items.sumOf { it.fatG }
}

enum class Verdict(val label: String) {
    GOOD("جيدة"),
    OK_WITH_CHANGES("مقبولة مع تعديل"),
    NOT_SUITABLE("غير مناسبة لهدفك");

    companion object {
        fun from(value: String): Verdict = entries.firstOrNull { it.name == value } ?: OK_WITH_CHANGES
    }
}
