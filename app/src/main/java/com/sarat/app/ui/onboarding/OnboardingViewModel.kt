package com.sarat.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Repository
import com.sarat.app.domain.Activity
import com.sarat.app.domain.DietGoal
import com.sarat.app.domain.Nutrition
import com.sarat.app.domain.Plan
import com.sarat.app.domain.Sex
import com.sarat.app.domain.UserProfile
import com.sarat.app.util.Dates
import com.sarat.app.util.fmt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repo: Repository,
    val editMode: Boolean,
) : ViewModel() {

    var step by mutableIntStateOf(1)
        private set

    var name by mutableStateOf("")
    var sex by mutableStateOf(Sex.MALE)
    var age by mutableStateOf("")
    var height by mutableStateOf("")
    var weight by mutableStateOf("")
    var activity by mutableStateOf(Activity.MODERATE)
    var goals by mutableStateOf(setOf<DietGoal>())
    var thyroidMedMinute by mutableStateOf<Int?>(null)
    var targetWeight by mutableStateOf("")
    var weeklyRate by mutableStateOf(0.5)

    var loaded by mutableStateOf(!editMode)
        private set

    private var existing: UserProfile? = null
    private var originalWeight: Double? = null

    init {
        if (editMode) viewModelScope.launch {
            val p = repo.getProfile()
            val latest = repo.weights.first().lastOrNull()?.kg
            if (p != null) {
                existing = p
                name = p.name
                sex = p.sex
                age = p.age.toString()
                height = p.heightCm.fmt()
                val current = latest ?: p.startWeightKg
                originalWeight = current
                weight = current.fmt()
                activity = p.activity
                goals = p.goals
                thyroidMedMinute = p.thyroidMedMinute
                targetWeight = p.targetWeightKg.fmt()
                weeklyRate = p.weeklyRateKg
            }
            loaded = true
        }
    }

    private val ageN get() = age.toIntOrNull()
    private val heightN get() = height.toDoubleOrNull()
    private val weightN get() = weight.toDoubleOrNull()
    private val targetN get() = targetWeight.toDoubleOrNull()

    val ageError: String?
        get() = ageN?.let { if (it < 18) "التطبيق مصمم للبالغين (١٨ سنة فما فوق)" else if (it > 100) "تأكد من العمر" else null }
    val heightError: String? get() = heightN?.let { if (it !in 120.0..230.0) "تأكد من الطول" else null }
    val weightError: String? get() = weightN?.let { if (it !in 35.0..300.0) "تأكد من الوزن" else null }

    val step1Valid: Boolean
        get() = name.isNotBlank() && ageN != null && heightN != null && weightN != null &&
            ageError == null && heightError == null && weightError == null

    val step2Valid: Boolean
        get() = goals.isNotEmpty() && (DietGoal.HYPOTHYROID !in goals || thyroidMedMinute != null)

    val minHealthyWeight: Double? get() = heightN?.let { Nutrition.minHealthyWeight(it) }

    val targetError: String?
        get() {
            val t = targetN ?: return null
            val w = weightN ?: return null
            val min = minHealthyWeight ?: return null
            return when {
                t >= w -> "الوزن المستهدف لازم يكون أقل من وزنك الحالي"
                t < min -> "هذا الوزن أقل من الوزن الصحي لطولك (أقل شي ${min.fmt(0)} كغم)"
                else -> null
            }
        }

    val step3Valid: Boolean get() = targetN != null && targetError == null

    fun toggleGoal(goal: DietGoal) {
        goals = if (goal in goals) goals - goal else goals + goal
        if (DietGoal.HYPOTHYROID !in goals) thyroidMedMinute = null
    }

    /** الاحتياج اليومي التقريبي لعرضه بالخطوة الأولى. */
    val tdeePreview: Int?
        get() {
            if (!step1Valid) return null
            return (Nutrition.bmr(sex, weightN!!, heightN!!, ageN!!) * activity.factor +
                (existing?.tdeeAdjustment ?: 0)).toInt()
        }

    private fun draftProfile(): UserProfile? {
        if (!step1Valid) return null
        val today = Dates.todayEpoch()
        val prev = existing
        return UserProfile(
            name = name.trim(),
            sex = sex,
            age = ageN!!,
            heightCm = heightN!!,
            startWeightKg = prev?.startWeightKg ?: weightN!!,
            activity = activity,
            goals = goals,
            targetWeightKg = targetN ?: weightN!!,
            weeklyRateKg = weeklyRate,
            startEpochDay = prev?.startEpochDay ?: today,
            tdeeAdjustment = prev?.tdeeAdjustment ?: 0,
            thyroidMedMinute = thyroidMedMinute,
        )
    }

    val planPreview: Plan?
        get() {
            if (!step3Valid) return null
            val p = draftProfile() ?: return null
            return Nutrition.plan(p, weightN!!, Dates.todayEpoch())
        }

    fun next() { if (step < 3) step++ }
    fun back(): Boolean = if (step > 1) { step--; true } else false

    fun save(onDone: () -> Unit) {
        val p = draftProfile() ?: return
        viewModelScope.launch {
            repo.saveProfile(p)
            val w = weightN!!
            if (editMode && originalWeight != null && w != originalWeight) {
                repo.addWeight(Dates.todayEpoch(), w)
            }
            onDone()
        }
    }
}
