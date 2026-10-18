package com.sarat.app.data.gemini

import com.sarat.app.domain.DietGoal
import com.sarat.app.domain.MealBudget
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

@Serializable
data class PlanIngredient(
    val name: String,
    /** الكمية بكلام بسيط: «٢ بيضة»، «نص كوب». */
    val amount: String = "",
    val grams: Int = 0,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    /** موجود بالبيت (true) أو يحتاج يشتريه (false). */
    val available: Boolean = true,
) {
    fun scaled(factor: Double): PlanIngredient = copy(
        grams = (grams * factor).roundToInt(),
        calories = (calories * factor).roundToInt(),
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )
}

@Serializable
data class MealSuggestion(
    val possible: Boolean = true,
    val dishName: String = "",
    /** ليش هذي الوجبة مناسبة، بجملة. */
    val summary: String = "",
    val ingredients: List<PlanIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val prepMinutes: Int = 0,
    val verdict: String = "GOOD",
    val note: String = "",
) {
    val totalCalories: Int get() = ingredients.sumOf { it.calories }
    val totalProtein: Double get() = ingredients.sumOf { it.proteinG }
    val totalCarbs: Double get() = ingredients.sumOf { it.carbsG }
    val totalFat: Double get() = ingredients.sumOf { it.fatG }

    /** نحوّلها لنفس شكل الوجبة المحللة حتى تنحفظ بالسجل. */
    fun toAnalysis(): MealAnalysis = MealAnalysis(
        isFood = true,
        mealName = dishName,
        items = ingredients.map {
            FoodItem(it.name, it.amount, it.grams, it.calories, it.proteinG, it.carbsG, it.fatG, fit = "GOOD")
        },
        verdict = verdict,
        verdictReason = summary,
        suggestion = "",
    )
}

object PlanPrompts {

    val system = """
You are a home-cooking nutrition coach inside an Arabic weight-loss app used mainly in Iraq.
You know Iraqi and Middle Eastern home cooking well.

Task: suggest ONE meal the user can cook now from the ingredients they have at home, sized to a calorie budget.

Rules:
- Use only the listed ingredients, plus basic staples every kitchen has: salt, pepper, common spices, water, lemon, and a small amount of cooking oil (count the oil's calories).
- If a good meal really needs one missing item, you may add at most ONE, with available=false. Prefer meals with none missing.
- Total calories must be within about 10% of the budget. Aim for the protein, carbs and fat targets given, protein first.
- Give realistic home amounts in simple Arabic ("٢ بيضة", "نص كوب", "ملعقة كبيرة") and grams.
- Steps: short, practical, in order, 3 to 7 steps, simple Iraqi-friendly Arabic.
- Respect the user's diet goals (INSULIN_RESISTANCE: low sugar and refined carbs, more fibre and protein; HYPOTHYROID: balanced, avoid soy/coffee/high calcium near the medication time; OBESITY_LIPIDS: low sugar, little saturated fat, no deep frying).
- Fit the meal type: breakfast foods for breakfast, light for a snack, lighter for a late dinner.
- If the user names a dish they want, make that dish (or the closest healthy version) adapted to the budget and ingredients; say in note what you changed.
- If the user asks to modify a previous suggestion, apply the change and keep the rest.
- If no sensible meal is possible from the ingredients, set possible=false and explain in note what to add.
- All user-facing text in simple Arabic. verdict: GOOD, OK_WITH_CHANGES or NOT_SUITABLE for the goals.
""".trimIndent()

    fun userText(
        budget: MealBudget,
        goals: Set<DietGoal>,
        pantry: String,
        wish: String?,
        nowMinute: Int,
        thyroidMedMinute: Int?,
        previous: MealSuggestion?,
        change: String?,
        exclude: List<String>,
        feedback: com.sarat.app.data.PlanFeedback? = null,
    ): String = buildString {
        appendLine("Meal type: ${budget.slot.english}")
        appendLine("Current time: ${hhmm(nowMinute)}")
        appendLine("Calorie budget for this meal: ${budget.kcal}")
        appendLine("Targets for this meal: protein ${budget.proteinG} g, carbs ${budget.carbsG} g, fat ${budget.fatG} g")
        appendLine("Calories left today before this meal: ${budget.remainingBefore}")
        if (budget.later.isNotEmpty()) {
            appendLine("Meals still to come today: " + budget.later.joinToString { "${it.first.english} ~${it.second} kcal" })
        }
        appendLine("Diet goals: ${goals.joinToString { it.name }.ifEmpty { "GENERAL_WEIGHT_LOSS" }}")
        thyroidMedMinute?.let { appendLine("Thyroid medication time: ${hhmm(it)}") }
        appendLine("Ingredients at home (Arabic): $pantry")
        if (!wish.isNullOrBlank()) appendLine("The user wants this dish: $wish")
        if (exclude.isNotEmpty()) appendLine("Suggest something different from: ${exclude.joinToString()}")
        if (feedback != null && !feedback.isEmpty) {
            if (feedback.disliked.isNotEmpty())
                appendLine("The user did NOT like these earlier suggestions, avoid them and similar dishes: ${feedback.disliked.joinToString()}")
            if (feedback.liked.isNotEmpty())
                appendLine("The user liked these earlier suggestions (similar styles are welcome): ${feedback.liked.joinToString()}")
            if (feedback.hungry.isNotEmpty())
                appendLine("The user was still hungry after: ${feedback.hungry.joinToString()}. Make meals more filling within the same calories: more protein, fibre, vegetables and volume, fewer calorie-dense small portions.")
        }
        if (previous != null && !change.isNullOrBlank()) {
            appendLine("Previous suggestion: ${previous.dishName} — " +
                previous.ingredients.joinToString { "${it.name} ${it.amount}" })
            appendLine("The user asks to change it: $change")
        }
    }

    private fun hhmm(m: Int) = String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60)

    val schema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("possible") { put("type", "BOOLEAN") }
            putJsonObject("dishName") { put("type", "STRING") }
            putJsonObject("summary") { put("type", "STRING") }
            putJsonObject("ingredients") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "STRING") }
                        putJsonObject("amount") { put("type", "STRING") }
                        putJsonObject("grams") { put("type", "INTEGER") }
                        putJsonObject("calories") { put("type", "INTEGER") }
                        putJsonObject("proteinG") { put("type", "NUMBER") }
                        putJsonObject("carbsG") { put("type", "NUMBER") }
                        putJsonObject("fatG") { put("type", "NUMBER") }
                        putJsonObject("available") { put("type", "BOOLEAN") }
                    }
                    putJsonArray("required") {
                        add("name"); add("amount"); add("grams"); add("calories")
                        add("proteinG"); add("carbsG"); add("fatG"); add("available")
                    }
                }
            }
            putJsonObject("steps") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
            putJsonObject("prepMinutes") { put("type", "INTEGER") }
            putJsonObject("verdict") {
                put("type", "STRING")
                put("format", "enum")
                putJsonArray("enum") { add("GOOD"); add("OK_WITH_CHANGES"); add("NOT_SUITABLE") }
            }
            putJsonObject("note") { put("type", "STRING") }
        }
        putJsonArray("required") {
            add("possible"); add("dishName"); add("summary"); add("ingredients")
            add("steps"); add("prepMinutes"); add("verdict"); add("note")
        }
    }
}
