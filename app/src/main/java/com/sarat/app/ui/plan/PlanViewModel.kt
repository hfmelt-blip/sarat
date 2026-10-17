package com.sarat.app.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.AppPrefs
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
        request(previous = null, changeText = null)
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
                )
                val result = gemini.suggestMeal(text)
                suggestion = result
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
