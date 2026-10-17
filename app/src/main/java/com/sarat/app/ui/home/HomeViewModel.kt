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
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: Repository) : ViewModel() {

    private val todayFlow = Dates.todayFlow()

    private val mealsToday = todayFlow.flatMapLatest { day -> repo.mealsForDay(day).map { day to it } }

    val state: StateFlow<HomeState?> = combine(repo.profile, repo.weights, mealsToday) { profile, weights, (day, meals) ->
        if (profile == null) return@combine null
        val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
        val lastWeighIn = weights.lastOrNull()?.epochDay ?: profile.startEpochDay
        HomeState(
            today = day,
            profile = profile,
            plan = Nutrition.plan(profile, current, day),
            meals = meals,
            consumed = meals.sumOf { it.calories },
            protein = meals.sumOf { it.proteinG },
            carbs = meals.sumOf { it.carbsG },
            fat = meals.sumOf { it.fatG },
            weighInDue = Nutrition.isWeighInDue(lastWeighIn, day),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteMeal(id: Long) {
        viewModelScope.launch { repo.deleteMeal(id) }
    }
}
