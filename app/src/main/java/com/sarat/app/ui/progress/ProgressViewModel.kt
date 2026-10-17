package com.sarat.app.ui.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Repository
import com.sarat.app.data.db.WeightEntity
import com.sarat.app.domain.Calibration
import com.sarat.app.domain.Nutrition
import com.sarat.app.domain.Plan
import com.sarat.app.domain.UserProfile
import com.sarat.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressState(
    val profile: UserProfile,
    val weights: List<WeightEntity>,
    val currentWeight: Double,
    val plan: Plan,
    val today: Long,
    val lastWeighIn: Long,
)

data class WeighInResult(
    val newWeight: Double,
    val previousWeight: Double,
    val calibration: Calibration,
    val oldPlan: Plan,
    val newPlan: Plan,
    /** موعد الوصول إذا استمر النزول بنفس الوتيرة الفعلية الملاحظة. */
    val projectedEtaEpochDay: Long?,
)

class ProgressViewModel(private val repo: Repository) : ViewModel() {

    val state: StateFlow<ProgressState?> = combine(repo.profile, repo.weights, Dates.todayFlow()) { profile, weights, today ->
        if (profile == null) return@combine null
        val current = weights.lastOrNull()?.kg ?: profile.startWeightKg
        ProgressState(
            profile = profile,
            weights = weights,
            currentWeight = current,
            plan = Nutrition.plan(profile, current, today),
            today = today,
            lastWeighIn = weights.lastOrNull()?.epochDay ?: profile.startEpochDay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var weightInput by mutableStateOf("")
    var result by mutableStateOf<WeighInResult?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    val inputValue: Double? get() = weightInput.toDoubleOrNull()?.takeIf { it in 35.0..300.0 }

    fun onInput(value: String) {
        weightInput = value
        result = null
        saved = false
    }

    fun calculate() {
        val newWeight = inputValue ?: return
        viewModelScope.launch {
            val profile = repo.getProfile() ?: return@launch
            val today = Dates.todayEpoch()
            val weights = repo.weights.first()
            // نقارن مع آخر قياس قبل اليوم
            val previous = weights.lastOrNull { it.epochDay < today }
                ?: WeightEntity(epochDay = profile.startEpochDay, kg = profile.startWeightKg)
            val intake = repo.totalsBetween(previous.epochDay, today)
            val calibration = Nutrition.calibrate(
                p = profile,
                previousWeightKg = previous.kg,
                previousEpochDay = previous.epochDay,
                newWeightKg = newWeight,
                newEpochDay = today,
                intakeByDay = intake,
            )
            val currentWeight = weights.lastOrNull()?.kg ?: profile.startWeightKg
            val remaining = newWeight - profile.targetWeightKg
            val dailyLoss = if (calibration.days > 0) calibration.lostKg / calibration.days else 0.0
            val projected = if (remaining > 0 && dailyLoss > 0.0) {
                today + kotlin.math.ceil(remaining / dailyLoss).toLong()
            } else null
            result = WeighInResult(
                newWeight = newWeight,
                previousWeight = previous.kg,
                calibration = calibration,
                oldPlan = Nutrition.plan(profile, currentWeight, today),
                newPlan = Nutrition.plan(profile.copy(tdeeAdjustment = calibration.newAdjustment), newWeight, today),
                projectedEtaEpochDay = projected,
            )
        }
    }

    fun save() {
        val r = result ?: return
        viewModelScope.launch {
            repo.addWeight(Dates.todayEpoch(), r.newWeight)
            if (r.calibration.reliable) repo.setAdjustment(r.calibration.newAdjustment)
            saved = true
            weightInput = ""
            result = null
        }
    }
}
