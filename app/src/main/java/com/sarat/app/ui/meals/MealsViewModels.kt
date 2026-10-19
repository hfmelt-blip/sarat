package com.sarat.app.ui.meals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Meal
import com.sarat.app.data.Repository
import com.sarat.app.data.gemini.FoodItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** كل الوجبات المسجّلة، مجمّعة حسب اليوم للعرض بجدول. */
class MealsViewModel(private val repo: Repository) : ViewModel() {

    data class DayGroup(val epochDay: Long, val meals: List<Meal>) {
        val calories = meals.sumOf { it.calories }
        val protein = meals.sumOf { it.proteinG }
        val carbs = meals.sumOf { it.carbsG }
        val fat = meals.sumOf { it.fatG }
    }

    val groups: StateFlow<List<DayGroup>> = repo.allMeals
        .map { list -> list.groupBy { it.epochDay }.map { (day, meals) -> DayGroup(day, meals) }.sortedByDescending { it.epochDay } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) { viewModelScope.launch { repo.deleteMeal(id) } }
}

/** تعديل وجبة مسجّلة: الاسم، كمية كل مكوّن، حذف أو إضافة مكوّن. */
class EditMealViewModel(private val repo: Repository, private val id: Long) : ViewModel() {
    var title by mutableStateOf("")
    var source by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    val items = mutableListOf<FoodItem>().toMutableStateList()

    init {
        viewModelScope.launch {
            val m = repo.observeMealOnce(id)
            if (m != null) {
                title = m.title
                source = m.source
                items.addAll(m.items)
            }
            loaded = true
        }
    }

    val totalCalories get() = items.sumOf { it.calories }
    val totalProtein get() = items.sumOf { it.proteinG }
    val totalCarbs get() = items.sumOf { it.carbsG }
    val totalFat get() = items.sumOf { it.fatG }
    val canSave get() = loaded && items.isNotEmpty()

    fun scale(index: Int, factor: Double) {
        val it = items.getOrNull(index) ?: return
        val next = it.scaled(factor)
        if (next.calories <= 0 && factor < 1) return
        items[index] = next
    }

    fun removeItem(index: Int) { if (index in items.indices) items.removeAt(index) }

    fun addItem(item: FoodItem) { items.add(item) }

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            repo.updateMeal(id, title.trim(), items.toList())
            onDone()
        }
    }
}
