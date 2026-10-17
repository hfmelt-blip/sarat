package com.sarat.app.data

import com.sarat.app.data.db.AppDatabase
import com.sarat.app.data.db.MealEntity
import com.sarat.app.data.db.ProfileEntity
import com.sarat.app.data.db.WeightEntity
import com.sarat.app.data.gemini.FoodItem
import com.sarat.app.data.gemini.MealAnalysis
import com.sarat.app.domain.Activity
import com.sarat.app.domain.DietGoal
import com.sarat.app.domain.Sex
import com.sarat.app.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class Repository(private val db: AppDatabase) {

    private val json = Json { ignoreUnknownKeys = true }
    private val itemsSerializer = ListSerializer(FoodItem.serializer())

    // ───── الملف الشخصي ─────

    val profile: Flow<UserProfile?> = db.profileDao().observe().map { it?.toDomain() }

    suspend fun getProfile(): UserProfile? = db.profileDao().get()?.toDomain()

    /** يحفظ الملف، ويسجّل وزن البداية كأول قياس إذا ما كان أكو قياسات. */
    suspend fun saveProfile(p: UserProfile) {
        db.profileDao().upsert(p.toEntity())
        if (db.weightDao().latest() == null) {
            db.weightDao().insert(WeightEntity(epochDay = p.startEpochDay, kg = p.startWeightKg))
        }
    }

    suspend fun setAdjustment(value: Int) = db.profileDao().setAdjustment(value)

    // ───── الوجبات ─────

    fun mealsForDay(epochDay: Long): Flow<List<Meal>> =
        db.mealDao().observeDay(epochDay).map { list -> list.map { it.toMeal() } }

    val dailyTotals: Flow<Map<Long, Int>> =
        db.mealDao().observeDailyTotals().map { list -> list.associate { it.epochDay to it.total } }

    suspend fun totalsBetween(from: Long, until: Long): Map<Long, Int> =
        db.mealDao().totalsBetween(from, until).associate { it.epochDay to it.total }

    suspend fun addMeal(analysis: MealAnalysis, epochDay: Long, minuteOfDay: Int, source: String) {
        db.mealDao().insert(
            MealEntity(
                epochDay = epochDay,
                minuteOfDay = minuteOfDay,
                title = analysis.mealName.ifBlank { analysis.items.joinToString("، ") { it.name } },
                calories = analysis.totalCalories,
                proteinG = analysis.totalProtein,
                carbsG = analysis.totalCarbs,
                fatG = analysis.totalFat,
                verdict = analysis.verdict,
                verdictReason = analysis.verdictReason,
                suggestion = analysis.suggestion,
                itemsJson = json.encodeToString(itemsSerializer, analysis.items),
                source = source,
            )
        )
    }

    suspend fun deleteMeal(id: Long) = db.mealDao().delete(id)

    // ───── الوزن ─────

    val weights: Flow<List<WeightEntity>> = db.weightDao().observeAll()

    suspend fun addWeight(epochDay: Long, kg: Double) =
        db.weightDao().insert(WeightEntity(epochDay = epochDay, kg = kg))

    // ───── تحويلات ─────

    private fun MealEntity.toMeal() = Meal(
        id = id,
        epochDay = epochDay,
        minuteOfDay = minuteOfDay,
        title = title,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        verdict = verdict,
        source = source,
        items = runCatching { json.decodeFromString(itemsSerializer, itemsJson) }.getOrDefault(emptyList()),
    )

    private fun ProfileEntity.toDomain() = UserProfile(
        sex = Sex.valueOf(sex),
        age = age,
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        activity = Activity.valueOf(activity),
        goals = goals.split(',').filter { it.isNotBlank() }
            .mapNotNull { name -> DietGoal.entries.firstOrNull { it.name == name } }.toSet(),
        targetWeightKg = targetWeightKg,
        weeklyRateKg = weeklyRateKg,
        startEpochDay = startEpochDay,
        tdeeAdjustment = tdeeAdjustment,
        thyroidMedMinute = thyroidMedMinute,
    )

    private fun UserProfile.toEntity() = ProfileEntity(
        sex = sex.name,
        age = age,
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        activity = activity.name,
        goals = goals.joinToString(",") { it.name },
        targetWeightKg = targetWeightKg,
        weeklyRateKg = weeklyRateKg,
        startEpochDay = startEpochDay,
        tdeeAdjustment = tdeeAdjustment,
        thyroidMedMinute = thyroidMedMinute,
    )
}

data class Meal(
    val id: Long,
    val epochDay: Long,
    val minuteOfDay: Int,
    val title: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val verdict: String,
    val source: String,
    val items: List<FoodItem>,
)
