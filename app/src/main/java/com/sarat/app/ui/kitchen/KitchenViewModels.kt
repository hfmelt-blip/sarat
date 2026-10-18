package com.sarat.app.ui.kitchen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.BuiltinRecipes
import com.sarat.app.data.Repository
import com.sarat.app.data.SavedRecipe
import com.sarat.app.data.db.ShoppingEntity
import com.sarat.app.data.gemini.MealSuggestion
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipesViewModel(private val repo: Repository) : ViewModel() {
    val saved: StateFlow<List<SavedRecipe>> =
        repo.recipes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** تفاصيل وصفة محفوظة: الكمية، المشاركة، المشتريات، والتسجيل. */
class RecipeDetailViewModel(
    private val repo: Repository,
    private val id: Long,
    /** وصفة عراقية جاهزة بدل وصفة محفوظة. */
    val builtinId: String? = null,
    private val pantry: String = "",
) : ViewModel() {
    var recipe by mutableStateOf<SavedRecipe?>(null)
        private set
    var portion by mutableStateOf(1.0)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init { reload() }

    /** نعيد التحميل بعد الرجوع من شاشة التعديل. */
    fun reload() {
        viewModelScope.launch {
            recipe = if (builtinId != null) {
                val goals = repo.getProfile()?.goals ?: emptySet()
                BuiltinRecipes.byId(builtinId)?.let { SavedRecipe(0, "BUILTIN", it.toSuggestion(goals, pantry.takeIf { p -> p.isNotBlank() })) }
            } else repo.getRecipe(id)
        }
    }

    /** الوصفة بعد ضرب الكمية بعدد الحصص. */
    val scaled: MealSuggestion?
        get() = recipe?.recipe?.let { r -> r.copy(ingredients = r.ingredients.map { it.scaled(portion) }) }

    fun choosePortion(p: Double) { portion = p }

    fun addMissingToShopping() {
        val s = scaled ?: return
        viewModelScope.launch {
            val added = s.ingredients.filter { !it.available }.count { repo.addShopping(it.name, it.amount) }
            message = if (added > 0) "انضاف $added للمشتريات" else "الناقص موجود أصلاً بالقائمة"
        }
    }

    fun addAllToShopping() {
        val s = scaled ?: return
        viewModelScope.launch {
            val added = s.ingredients.count { repo.addShopping(it.name, it.amount) }
            message = if (added > 0) "انضاف $added للمشتريات" else "كل المكونات موجودة بالقائمة"
        }
    }

    fun eat(onDone: () -> Unit) {
        val s = scaled ?: return
        viewModelScope.launch {
            repo.addMeal(s.toAnalysis(), Dates.todayEpoch(), Dates.nowMinute(), "PLAN")
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteRecipe(id)
            onDone()
        }
    }

    fun clearMessage() { message = null }
}

class ShoppingViewModel(private val repo: Repository) : ViewModel() {
    val items: StateFlow<List<ShoppingEntity>> =
        repo.shopping.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var input by mutableStateOf("")

    fun add() {
        val text = input.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            // يقبل أكثر من شي مفصول بفاصلة
            text.split('،', ',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { repo.addShopping(it) }
            input = ""
        }
    }

    fun toggle(item: ShoppingEntity) {
        viewModelScope.launch { repo.setShoppingDone(item.id, !item.done) }
    }

    fun delete(item: ShoppingEntity) {
        viewModelScope.launch { repo.deleteShopping(item.id) }
    }

    fun clearBought() {
        viewModelScope.launch { repo.clearBoughtShopping() }
    }
}

/** سطر مكوّن بشاشة التعديل. السعرات نص حتى المستخدم يكدر يمسحها ويكتب. */
data class EditRow(
    val name: String = "",
    val amount: String = "",
    val kcal: String = "",
    val grams: Int = 0,
    val p: Double = 0.0,
    val c: Double = 0.0,
    val f: Double = 0.0,
    val available: Boolean = true,
)

/** إضافة وصفة يدوياً أو تعديل وصفة موجودة. تعديل وصفة عراقية جاهزة يحفظ نسخة إلك. */
class RecipeEditViewModel(
    private val repo: Repository,
    private val gemini: com.sarat.app.data.gemini.GeminiClient,
    private val id: Long,
    private val builtinId: String?,
) : ViewModel() {
    var title by mutableStateOf("")
    var prep by mutableStateOf("")
    var steps by mutableStateOf("")
    val rows = androidx.compose.runtime.mutableStateListOf<EditRow>()
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    val isNew get() = id == 0L && builtinId == null

    init {
        viewModelScope.launch {
            val source = when {
                builtinId != null -> BuiltinRecipes.byId(builtinId)?.toSuggestion(emptySet())
                id != 0L -> repo.getRecipe(id)?.recipe
                else -> null
            }
            if (source != null) {
                title = source.dishName
                prep = if (source.prepMinutes > 0) source.prepMinutes.toString() else ""
                steps = source.steps.joinToString("\n")
                rows.addAll(source.ingredients.map { EditRow(it.name, it.amount, it.calories.toString(), it.grams, it.proteinG, it.carbsG, it.fatG, it.available) })
            }
            if (rows.isEmpty()) rows.addAll(listOf(EditRow(), EditRow(), EditRow()))
        }
    }

    fun addRow() { rows.add(EditRow()) }
    fun removeRow(i: Int) { if (i in rows.indices) rows.removeAt(i) }
    fun updateName(i: Int, v: String) { rows.getOrNull(i)?.let { rows[i] = it.copy(name = v) } }
    fun updateAmount(i: Int, v: String) { rows.getOrNull(i)?.let { rows[i] = it.copy(amount = v) } }

    /** تغيير السعرات يدوياً يغيّر البروتين والكارب والدهون بنفس النسبة. */
    fun updateKcal(i: Int, v: String) {
        val r = rows.getOrNull(i) ?: return
        val clean = v.filter { it.isDigit() || it in '٠'..'٩' }.map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }.joinToString("").take(4)
        val old = r.kcal.toIntOrNull() ?: 0
        val new = clean.toIntOrNull() ?: 0
        val k = if (old > 0) new.toDouble() / old else 0.0
        rows[i] = if (old > 0) r.copy(kcal = clean, p = r.p * k, c = r.c * k, f = r.f * k, grams = (r.grams * k).toInt()) else r.copy(kcal = clean)
    }

    private val filled get() = rows.filter { it.name.isNotBlank() }
    val total get() = filled.sumOf { it.kcal.toIntOrNull() ?: 0 }
    val canSave get() = title.isNotBlank() && filled.isNotEmpty() && !loading

    /** يحسب سعرات ومغذيات المكونات بـ Gemini. */
    fun calculate() {
        val list = filled
        if (list.isEmpty() || loading) return
        loading = true
        message = null
        viewModelScope.launch {
            try {
                val profile = repo.getProfile()
                val text = "وصفة «${title.ifBlank { "وجبة" }}» لحصة وحدة. احسب كل مكوّن كسطر مستقل بنفس الاسم والكمية:\n" +
                    list.joinToString("\n") { "- ${it.name}" + if (it.amount.isNotBlank()) " (${it.amount})" else "" }
                val ctx = com.sarat.app.data.gemini.MealContext(
                    goals = profile?.goals ?: emptySet(), remainingCalories = 2000, dailyTarget = 2000,
                    nowMinuteOfDay = com.sarat.app.util.Dates.nowMinute(), thyroidMedMinute = null,
                )
                val result = gemini.analyzeText(text, ctx)
                if (result.items.isEmpty()) {
                    message = "ما كدرت أحسبها، جرّب تكتب الكميات أوضح."
                } else {
                    val kept = rows.filter { it.name.isNotBlank() }
                    rows.clear()
                    rows.addAll(result.items.mapIndexed { n, it ->
                        EditRow(it.name, it.portion.ifBlank { kept.getOrNull(n)?.amount.orEmpty() }, it.calories.toString(), it.grams, it.proteinG, it.carbsG, it.fatG, kept.getOrNull(n)?.available ?: true)
                    })
                    message = "انحسبت السعرات: $total سعرة للحصة"
                }
            } catch (e: com.sarat.app.data.gemini.GeminiException) {
                message = e.message
            } catch (e: Exception) {
                message = "صار خطأ: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun save(onDone: (Long) -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val goals = repo.getProfile()?.goals ?: emptySet()
            val base = MealSuggestion(
                possible = true,
                dishName = title.trim(),
                ingredients = filled.map {
                    com.sarat.app.data.gemini.PlanIngredient(
                        name = it.name.trim(), amount = it.amount.trim(), grams = it.grams,
                        calories = it.kcal.toIntOrNull() ?: 0, proteinG = it.p, carbsG = it.c, fatG = it.f,
                        available = it.available,
                    )
                },
                steps = steps.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() },
                prepMinutes = prep.toIntOrNull() ?: 0,
            )
            val hasMacros = base.ingredients.any { it.proteinG + it.carbsG + it.fatG > 0 }
            val (verdict, summary) = if (hasMacros) com.sarat.app.data.LocalJudge.judge(base, goals) else "GOOD" to ""
            val newId = repo.saveRecipe(base.copy(verdict = verdict, summary = summary), "USER", if (builtinId == null) id else 0)
            onDone(newId)
        }
    }
}
