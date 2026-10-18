package com.sarat.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.sarat.app.data.db.ShoppingEntity
import com.sarat.app.data.gemini.MealSuggestion

/** مشاركة نصوص (وصفة أو قائمة مشتريات)، وتفضّل واتساب إذا موجود. */
object TextShare {

    fun recipeText(s: MealSuggestion): String = buildString {
        appendLine("🍽 ${s.dishName}")
        appendLine("${s.totalCalories} سعرة · بروتين ${s.totalProtein.toInt()}غ · كارب ${s.totalCarbs.toInt()}غ · دهون ${s.totalFat.toInt()}غ")
        if (s.prepMinutes > 0) appendLine("وقت التحضير: حوالي ${s.prepMinutes} دقيقة")
        appendLine()
        appendLine("المقادير:")
        s.ingredients.forEach { i ->
            val amount = listOf(i.amount, if (i.grams > 0) "${i.grams} غم" else "").filter { it.isNotBlank() }.joinToString(" · ")
            appendLine("• ${i.name}" + if (amount.isNotBlank()) " ($amount)" else "")
        }
        if (s.steps.isNotEmpty()) {
            appendLine()
            appendLine("طريقة العمل:")
            s.steps.forEachIndexed { n, step -> appendLine("${n + 1}. $step") }
        }
        appendLine()
        append("من تطبيق «شد الهمة»")
    }

    fun shoppingText(items: List<ShoppingEntity>): String = buildString {
        appendLine("🛒 قائمة المشتريات")
        items.filter { !it.done }.forEach { i ->
            appendLine("• ${i.name}" + if (i.amount.isNotBlank()) " (${i.amount})" else "")
        }
        append("من تطبيق «شد الهمة»")
    }

    /** يفتح واتساب مباشرة، وإذا ما موجود يفتح قائمة المشاركة. */
    fun share(context: Context, text: String) {
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(Intent(base).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        context.startActivity(Intent.createChooser(base, "شارك عبر").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
