package com.sarat.app.domain

import com.sarat.app.data.gemini.MealSuggestion
import kotlin.math.roundToInt

/** يقارن مغذيات الوجبة المقترحة بأهداف الوجبة. */
object MacroCheck {
    data class Line(val label: String, val value: Int, val target: Int, val ok: Boolean, val hint: String)

    fun lines(s: MealSuggestion, b: MealBudget): List<Line> {
        val p = s.totalProtein.roundToInt()
        val c = s.totalCarbs.roundToInt()
        val f = s.totalFat.roundToInt()
        return listOf(
            Line("بروتين", p, b.proteinG, p >= b.proteinG * 0.85, if (p >= b.proteinG * 0.85) "" else "أقل من حاجتك"),
            Line("كارب", c, b.carbsG, c <= b.carbsG * 1.15 + 5, if (c <= b.carbsG * 1.15 + 5) "" else "أعلى من هدفك"),
            Line("دهون", f, b.fatG, f <= b.fatG * 1.3 + 3, if (f <= b.fatG * 1.3 + 3) "" else "أعلى من هدفك"),
        )
    }

    fun ok(s: MealSuggestion, b: MealBudget) = lines(s, b).all { it.ok }

    /** مقدار البعد عن الأهداف، للمقارنة بين اقتراحين. */
    fun distance(s: MealSuggestion, b: MealBudget): Double {
        val p = maxOf(0.0, b.proteinG - s.totalProtein) / maxOf(1, b.proteinG)
        val c = maxOf(0.0, s.totalCarbs - b.carbsG) / maxOf(1, b.carbsG)
        val f = maxOf(0.0, s.totalFat - b.fatG) / maxOf(1, b.fatG)
        val k = kotlin.math.abs(s.totalCalories - b.kcal).toDouble() / maxOf(1, b.kcal)
        return p * 2 + c * 1.5 + f + k
    }

    /** طلب تصحيح نرسله لـ Gemini إذا الاقتراح الأول ما التزم. */
    fun fixRequest(s: MealSuggestion, b: MealBudget): String =
        "Adjust the ingredient amounts so the meal has at least ${b.proteinG} g protein, at most ${b.carbsG} g carbs, " +
            "about ${b.fatG} g fat, and about ${b.kcal} kcal. Currently it has ${s.totalProtein.roundToInt()} g protein, " +
            "${s.totalCarbs.roundToInt()} g carbs, ${s.totalFat.roundToInt()} g fat. Keep the same dish if possible."
}
