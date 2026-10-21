package com.sarat.app.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.AppPrefs
import com.sarat.app.data.BuiltinRecipes
import com.sarat.app.data.dishKey
import com.sarat.app.data.Repository
import com.sarat.app.data.gemini.GeminiClient
import com.sarat.app.data.gemini.GeminiException
import com.sarat.app.data.gemini.MealSuggestion
import com.sarat.app.data.gemini.PlanPrompts
import com.sarat.app.domain.DietGoal
import com.sarat.app.domain.MealBudget
import com.sarat.app.domain.MealPlanner
import com.sarat.app.domain.MealSlot
import com.sarat.app.domain.Nutrition
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlanViewModel(
    private val repo: Repository,
    private val gemini: GeminiClient,
    private val prefs: AppPrefs,
) : ViewModel() {

    var pantry by mutableStateOf(prefs.pantry)
        private set
    var wish by mutableStateOf("")
    var change by mutableStateOf("")
    var mealsPerDay by mutableIntStateOf(prefs.mealsPerDay)
        private set
    var slot by mutableStateOf(MealPlanner.slotForTime(Dates.nowMinute(), prefs.mealsPerDay))
        private set
    var budget by mutableStateOf<MealBudget?>(null)
        private set
    var suggestion by mutableStateOf<MealSuggestion?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** رسالة قصيرة بعد الحفظ أو الإضافة للمشتريات. */
    var notice by mutableStateOf<String?>(null)
    /** الاقتراح الحالي انحفظ بالوصفات. */
    var savedCurrent by mutableStateOf(false)
        private set
    /** الاقتراح الحالي طلع من الوصفات المحفوظة بدون Gemini. */
    var offline by mutableStateOf(false)
        private set

    private var goals: Set<DietGoal> = emptySet()
    private var medMinute: Int? = null
    private val shown = mutableListOf<String>()

    init { refreshBudget() }

    fun onPantry(value: String) {
        pantry = value
        prefs.pantry = value
    }

    fun addToPantry(item: String) {
        val parts = pantry.split('،', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.any { it == item }) return
        onPantry((parts + item).joinToString("، "))
    }

    fun chooseMealsPerDay(n: Int) {
        mealsPerDay = n
        prefs.mealsPerDay = n
        val available = MealPlanner.slots(n)
        if (slot !in available) slot = MealPlanner.slotForTime(Dates.nowMinute(), n)
        refreshBudget()
    }

    fun chooseSlot(s: MealSlot) {
        slot = s
        refreshBudget()
    }

    private fun refreshBudget() {
        viewModelScope.launch {
            val profile = repo.getProfile() ?: return@launch
            goals = profile.goals
            medMinute = profile.thyroidMedMinute
            val today = Dates.todayEpoch()
            val weight = repo.weights.first().lastOrNull()?.kg ?: profile.startWeightKg
            val plan = Nutrition.plan(profile, weight, today)
            val meals = repo.mealsForDay(today).first()
            budget = MealPlanner.budget(
                slot = slot,
                mealsPerDay = mealsPerDay,
                target = plan.dailyTarget,
                consumed = meals.sumOf { it.calories },
                macros = plan.macros,
                consumedProtein = meals.sumOf { it.proteinG },
                consumedCarbs = meals.sumOf { it.carbsG },
                consumedFat = meals.sumOf { it.fatG },
            )
        }
    }

    /** اقتراح جديد. [another] = نفس الطلب بس بوجبة مختلفة عن اللي طلعت. */
    fun suggest(another: Boolean = false) {
        if (!another) shown.clear()
        // إذا الطلب اسم وصفة محفوظة، نطلعها مباشرة بدون إنترنت
        if (wish.isNotBlank() && !(another && offline)) {
            viewModelScope.launch {
                if (!suggestOfflineNow(onlyWish = true)) request(previous = null, changeText = null)
            }
            return
        }
        if (another && offline) { suggestOffline(); return }
        request(previous = null, changeText = null)
    }

    /** اقتراح من الوصفات العراقية والمحفوظة، بدون Gemini وبدون إنترنت. */
    fun suggestOffline() {
        if (loading) return
        viewModelScope.launch {
            if (!suggestOfflineNow(onlyWish = false)) {
                shown.clear()
                if (!suggestOfflineNow(onlyWish = false)) error = "ما لكيت وصفة محفوظة تناسب هالوجبة."
            }
        }
    }

    private suspend fun suggestOfflineNow(onlyWish: Boolean): Boolean {
        val b = budget ?: return false
        val wishKey = dishKey(wish)
        val pantryKey = dishKey(pantry)
        val feedback = repo.planFeedback()
        val disliked = feedback.disliked.map { dishKey(it) }.toSet()
        val excluded = shown.map { dishKey(it) }.toSet()

        data class Cand(val name: String, val baseKcal: Int, val keys: List<String>, val fits: Boolean, val saved: Boolean, val make: (Double) -> MealSuggestion)

        val cands = mutableListOf<Cand>()
        BuiltinRecipes.all.forEach { r ->
            cands += Cand(r.name, r.kcal, r.ingredients.map { it.key }.filter { it.isNotEmpty() }, b.slot in r.slots, false) { f ->
                r.toSuggestion(goals, pantry.takeIf { it.isNotBlank() }, f)
            }
        }
        repo.recipes.first().forEach { sr ->
            val r = sr.recipe
            if (cands.any { dishKey(it.name) == dishKey(r.dishName) }) cands.removeAll { dishKey(it.name) == dishKey(r.dishName) }
            cands += Cand(r.dishName, r.totalCalories, r.ingredients.map { it.name.split(' ').first() }, true, true) { f ->
                val items = r.ingredients.map { i ->
                    val first = dishKey(i.name.split(' ').first())
                    val scaled = if (f == 1.0) i else i.scaled(f)
                    scaled.copy(available = pantryKey.isBlank() || first.length < 2 || pantryKey.contains(first))
                }
                r.copy(
                    ingredients = items,
                    note = listOf(r.note, if (f != 1.0) "الكمية معدّلة لتناسب سعرات وجبتك." else "").filter { it.isNotBlank() }.joinToString(" "),
                )
            }
        }

        fun wishMatch(name: String): Boolean {
            if (wishKey.length < 2) return false
            val k = dishKey(name)
            return k.contains(wishKey) || wishKey.contains(k) || wishKey.split(' ').any { it.length >= 3 && k.contains(it) }
        }

        val pool = cands.filter { dishKey(it.name) !in excluded && it.baseKcal > 0 }
        val picked = if (onlyWish) {
            pool.filter { wishMatch(it.name) }.maxByOrNull { if (it.saved) 1 else 0 }
        } else {
            pool.maxByOrNull { c ->
                var score = 0.0
                if (wishMatch(c.name)) score += 100
                if (c.fits) score += 30
                if (c.saved) score += 5
                if (dishKey(c.name) in disliked) score -= 60
                if (pantryKey.isNotBlank() && c.keys.isNotEmpty()) {
                    score += 20.0 * c.keys.count { pantryKey.contains(dishKey(it)) } / c.keys.size
                }
                // الأقرب للسعرات والبروتين والكارب والدهون المطلوبة أفضل
                val f = (b.kcal.toDouble() / c.baseKcal).coerceIn(0.5, 1.5)
                score -= com.sarat.app.domain.MacroCheck.distance(c.make(f), b) * 25
                score
            }
        } ?: return false

        val raw = b.kcal.toDouble() / picked.baseKcal
        val factor = (Math.round(raw * 4) / 4.0).coerceIn(0.5, 1.5)
        val result = picked.make(factor)
        suggestion = result
        offline = true
        error = null
        savedCurrent = picked.saved
        shown += result.dishName
        return true
    }

    /** تعديل الاقتراح الحالي حسب طلب المستخدم. */
    fun applyChange() {
        val current = suggestion ?: return
        if (change.isBlank()) return
        request(previous = current, changeText = change.trim())
    }

    private fun request(previous: MealSuggestion?, changeText: String?) {
        val b = budget ?: return
        if (pantry.isBlank() || loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val text = PlanPrompts.userText(
                    budget = b, goals = goals, pantry = pantry, wish = wish.takeIf { it.isNotBlank() },
                    nowMinute = Dates.nowMinute(), thyroidMedMinute = medMinute,
                    previous = previous, change = changeText, exclude = shown.toList(),
                    feedback = runCatching { repo.planFeedback() }.getOrNull(),
                )
                var result = gemini.suggestMeal(text)
                // إذا الوجبة ما راعت البروتين والكارب والدهون، نطلب تصحيح مرة وحدة
                if (result.possible && result.ingredients.isNotEmpty() && !com.sarat.app.domain.MacroCheck.ok(result, b)) {
                    val fixText = PlanPrompts.userText(
                        budget = b, goals = goals, pantry = pantry, wish = wish.takeIf { it.isNotBlank() },
                        nowMinute = Dates.nowMinute(), thyroidMedMinute = medMinute,
                        previous = result, change = com.sarat.app.domain.MacroCheck.fixRequest(result, b), exclude = emptyList(),
                    )
                    val fixed = runCatching { gemini.suggestMeal(fixText) }.getOrNull()
                    if (fixed != null && fixed.possible && fixed.ingredients.isNotEmpty() &&
                        com.sarat.app.domain.MacroCheck.distance(fixed, b) < com.sarat.app.domain.MacroCheck.distance(result, b)
                    ) result = fixed
                }
                suggestion = result
                savedCurrent = false
                offline = false
                if (result.dishName.isNotBlank()) shown += result.dishName
                if (changeText != null) change = ""
            } catch (e: GeminiException) {
                error = e.message
            } catch (e: Exception) {
                error = "صار خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun scaleIngredient(index: Int, factor: Double) {
        val s = suggestion ?: return
        val list = s.ingredients.toMutableList()
        val item = list.getOrNull(index) ?: return
        val next = item.scaled(factor)
        if (next.calories <= 0 && factor < 1) return
        list[index] = next
        suggestion = s.copy(ingredients = list)
    }

    fun removeIngredient(index: Int) {
        val s = suggestion ?: return
        suggestion = s.copy(ingredients = s.ingredients.filterIndexed { i, _ -> i != index })
    }

    fun saveRecipe() {
        val s = suggestion ?: return
        if (!s.possible || s.ingredients.isEmpty()) return
        viewModelScope.launch {
            repo.saveRecipe(s, "AI")
            savedCurrent = true
            notice = "انحفظت بوصفاتي"
        }
    }

    fun addToShopping(index: Int) {
        val item = suggestion?.ingredients?.getOrNull(index) ?: return
        viewModelScope.launch {
            notice = if (repo.addShopping(item.name, item.amount)) "انضاف «${item.name}» للمشتريات" else "«${item.name}» موجود أصلاً بالقائمة"
        }
    }

    fun addMissingToShopping() {
        val s = suggestion ?: return
        viewModelScope.launch {
            val added = s.ingredients.filter { !it.available }.count { repo.addShopping(it.name, it.amount) }
            notice = if (added > 0) "انضاف $added للمشتريات" else "الناقص موجود أصلاً بالقائمة"
        }
    }

    /** يسجّل الوجبة المقترحة كوجبة مأكولة. */
    fun eat(onSaved: () -> Unit) {
        val s = suggestion ?: return
        if (s.ingredients.isEmpty()) return
        viewModelScope.launch {
            repo.addMeal(s.toAnalysis(), Dates.todayEpoch(), Dates.nowMinute(), "PLAN")
            onSaved()
        }
    }
}
