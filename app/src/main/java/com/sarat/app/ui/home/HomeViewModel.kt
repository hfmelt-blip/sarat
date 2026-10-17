package com.sarat.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Meal
import com.sarat.app.data.Repository
import com.sarat.app.domain.Nutrition
import com.sarat.app.domain.Plan
import com.sarat.app.domain.UserProfile
import com.sarat.app.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val today: Long,
    val profile: UserProfile,
    val plan: Plan,
    val meals: List<Meal>,
    val consumed: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val weighInDue: Boolean,
    val streak: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: Repository, private val thumbs: com.sarat.app.util.Thumbs) : ViewModel() {

    private val todayFlow = Dates.todayFlow()

    private val mealsToday = todayFlow.flatMapLatest { day -> repo.mealsForDay(day).map { day to it } }

    val state: StateFlow<HomeState?> = combine(
        repo.profile, repo.weights, mealsToday, repo.dailyTotals,
    ) { profile, weights, (day, meals), totals ->
        if (profile == null) return@combine null
        val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
        val lastWeighIn = weights.lastOrNull()?.epochDay ?: profile.startEpochDay
        val plan = Nutrition.plan(profile, current, day)
        val recent = Nutrition.buildHistory(
            totals, maxOf(profile.startEpochDay, day - 60), day, plan.dailyTarget, plan.minimumCalories,
        )
        HomeState(
            today = day,
            profile = profile,
            plan = plan,
            meals = meals,
            consumed = meals.sumOf { it.calories },
            protein = meals.sumOf { it.proteinG },
            carbs = meals.sumOf { it.carbsG },
            fat = meals.sumOf { it.fatG },
            weighInDue = Nutrition.isWeighInDue(lastWeighIn, day),
            streak = Nutrition.currentStreak(recent),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favorites: StateFlow<List<Meal>> =
        repo.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** يسجّل وجبة مفضلة بضغطة، بنفس سعراتها وبدون Gemini. */
    fun logFavorite(meal: Meal) {
        viewModelScope.launch {
            val id = repo.logAgain(meal.id, Dates.todayEpoch(), Dates.nowMinute()) ?: return@launch
            runCatching { thumbs.copy(meal.id, id) }
        }
    }

    fun toggleFavorite(meal: Meal) {
        viewModelScope.launch { repo.setFavorite(meal.title, !meal.favorite) }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch { repo.deleteMeal(id); thumbs.delete(id) }
    }
}
