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

    /** يحفظ الوجبة ويرجع رقمها (يُستخدم لاسم الصورة المصغّرة). */
    suspend fun addMeal(analysis: MealAnalysis, epochDay: Long, minuteOfDay: Int, source: String): Long {
        return db.mealDao().insert(
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

    val favorites: Flow<List<Meal>> = db.mealDao().observeFavorites().map { list -> list.map { it.toMeal() } }

    /** تثبيت أو إلغاء تثبيت وجبة كمفضلة (كل الوجبات بنفس الاسم). */
    suspend fun setFavorite(title: String, value: Boolean) = db.mealDao().setFavorite(title, value)

    /** يسجّل نسخة من وجبة سابقة بتاريخ ووقت اليوم، ويرجع رقمها الجديد. */
    suspend fun logAgain(mealId: Long, epochDay: Long, minuteOfDay: Int): Long? {
        val m = db.mealDao().get(mealId) ?: return null
        return db.mealDao().insert(m.copy(id = 0, epochDay = epochDay, minuteOfDay = minuteOfDay))
    }

    // ───── تصحيحات المستخدم ─────

    /** المكونات اللي صححها المستخدم لهذي الوجبة قبل (لحصة وحدة)، إن وُجدت. */
    suspend fun getCorrection(dishName: String): List<FoodItem>? {
        val key = dishKey(dishName).ifBlank { return null }
        val c = db.correctionDao().get(key) ?: return null
        return runCatching { json.decodeFromString(itemsSerializer, c.itemsJson) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    suspend fun saveCorrection(dishName: String, itemsPerServing: List<FoodItem>) {
        val key = dishKey(dishName).ifBlank { return }
        db.correctionDao().upsert(
            com.sarat.app.data.db.CorrectionEntity(
                dishKey = key,
                title = dishName.trim(),
                itemsJson = json.encodeToString(itemsSerializer, itemsPerServing),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    // ───── الوصفات المحفوظة ─────

    val recipes: Flow<List<SavedRecipe>> = db.recipeDao().observeAll().map { list -> list.mapNotNull { it.toRecipe() } }

    suspend fun getRecipe(id: Long): SavedRecipe? = db.recipeDao().get(id)?.toRecipe()

    /** يحفظ الوصفة (وإذا موجودة بنفس الاسم يحدّثها) ويرجع رقمها. */
    suspend fun saveRecipe(s: com.sarat.app.data.gemini.MealSuggestion, source: String = "AI", id: Long = 0): Long {
        val payload = json.encodeToString(com.sarat.app.data.gemini.MealSuggestion.serializer(), s)
        val existing = if (id != 0L) db.recipeDao().get(id) else db.recipeDao().findByTitle(s.dishName)
        return if (existing != null) {
            db.recipeDao().upsert(existing.copy(title = s.dishName, json = payload))
            existing.id
        } else {
            db.recipeDao().insert(
                com.sarat.app.data.db.RecipeEntity(
                    title = s.dishName, json = payload, source = source, createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun deleteRecipe(id: Long) = db.recipeDao().delete(id)

    private fun com.sarat.app.data.db.RecipeEntity.toRecipe(): SavedRecipe? =
        runCatching {
            SavedRecipe(id, source, json.decodeFromString(com.sarat.app.data.gemini.MealSuggestion.serializer(), this.json))
        }.getOrNull()

    // ───── قائمة المشتريات ─────

    val shopping: Flow<List<com.sarat.app.data.db.ShoppingEntity>> = db.shoppingDao().observeAll()

    /** يضيف للقائمة إذا مو موجود أصلاً (وما مشتري). يرجع true إذا انضاف. */
    suspend fun addShopping(name: String, amount: String = ""): Boolean {
        val n = name.trim()
        if (n.isEmpty() || db.shoppingDao().countOpen(n) > 0) return false
        db.shoppingDao().insert(
            com.sarat.app.data.db.ShoppingEntity(name = n, amount = amount.trim(), done = false, createdAt = System.currentTimeMillis())
        )
        return true
    }

    suspend fun setShoppingDone(id: Long, done: Boolean) = db.shoppingDao().setDone(id, done)
    suspend fun deleteShopping(id: Long) = db.shoppingDao().delete(id)
    suspend fun clearBoughtShopping() = db.shoppingDao().clearDone()

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
        favorite = favorite,
        items = runCatching { json.decodeFromString(itemsSerializer, itemsJson) }.getOrDefault(emptyList()),
    )

    private fun ProfileEntity.toDomain() = UserProfile(
        name = name,
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
        name = name.trim(),
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
    val favorite: Boolean = false,
    val items: List<FoodItem>,
)

/** مفتاح موحّد لاسم الوجبة: يتجاهل التشكيل والهمزات والتاء المربوطة والمسافات الزايدة. */
fun dishKey(name: String): String = name.trim().lowercase()
    .replace(Regex("[\\u064B-\\u065F\\u0640]"), "")
    .replace(Regex("[أإآ]"), "ا")
    .replace('ة', 'ه')
    .replace('ى', 'ي')
    .replace(Regex("\\s+"), " ")

data class SavedRecipe(
    val id: Long,
    /** AI | USER | BUILTIN */
    val source: String,
    val recipe: com.sarat.app.data.gemini.MealSuggestion,
)
