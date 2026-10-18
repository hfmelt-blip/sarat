package com.sarat.app.ui.meal

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarat.app.data.Repository
import com.sarat.app.data.gemini.FoodItem
import com.sarat.app.data.gemini.GeminiClient
import com.sarat.app.data.gemini.GeminiException
import com.sarat.app.data.gemini.MealAnalysis
import com.sarat.app.data.gemini.MealContext
import com.sarat.app.domain.DietGoal
import com.sarat.app.domain.Nutrition
import com.sarat.app.util.Dates
import com.sarat.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class MealViewModel(
    private val app: Application,
    private val repo: Repository,
    private val gemini: GeminiClient,
    private val thumbs: com.sarat.app.util.Thumbs,
) : ViewModel() {

    /** الصورة الأصلية (مصغّرة) حتى نحفظ منها صورة صغيرة للسجل. */
    private var photo: Bitmap? = null

    var description by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var analysis by mutableStateOf<MealAnalysis?>(null)
        private set
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set

    /** السعرات المتبقية قبل هذه الوجبة. */
    var remainingBefore by mutableStateOf(0)
        private set
    var goals by mutableStateOf(setOf<DietGoal>())
        private set

    /** وقت الوجبة يتثبت أول ما يبدأ التحليل حتى ما يتغير تاريخها. */
    private var mealDay = Dates.todayEpoch()
    private var mealMinute = Dates.nowMinute()
    private var source = "TEXT"
    private var lastPhoto: Uri? = null
    private var started = false

    val remainingAfter: Int get() = remainingBefore - (analysis?.totalCalories ?: 0)

    /** عدد الحصص (١ = الكمية اللي قدّرها Gemini). */
    var portion by mutableStateOf(1.0)
        private set

    /** تصحيح سابق للمستخدم لنفس الوجبة (لحصة وحدة)، إن وُجد. */
    var savedCorrection by mutableStateOf<List<FoodItem>?>(null)
        private set
    var correctionApplied by mutableStateOf(false)
        private set

    /** اسم الوجبة مثل ما سمّاها Gemini، نستخدمه مفتاح للتصحيحات. */
    private var originalName = ""

    /** المستخدم عدّل المكونات (حتى نحفظ تصحيحه). */
    private var edited = false

    private suspend fun buildContext(): MealContext {
        mealDay = Dates.todayEpoch()
        mealMinute = Dates.nowMinute()
        val profile = repo.getProfile() ?: throw GeminiException("أكمل ملفك الشخصي أولاً.")
        val weight = repo.weights.first().lastOrNull()?.kg ?: profile.startWeightKg
        val plan = Nutrition.plan(profile, weight, mealDay)
        val consumed = repo.mealsForDay(mealDay).first().sumOf { it.calories }
        remainingBefore = plan.dailyTarget - consumed
        goals = profile.goals
        return MealContext(
            goals = profile.goals,
            remainingCalories = max(0, remainingBefore),
            dailyTarget = plan.dailyTarget,
            nowMinuteOfDay = mealMinute,
            thyroidMedMinute = profile.thyroidMedMinute,
        )
    }

    /** يُستدعى مرة وحدة عند فتح شاشة الصورة. */
    fun startPhoto(uri: Uri) {
        if (started) return
        started = true
        analyzePhoto(uri)
    }

    fun analyzePhoto(uri: Uri) {
        source = "PHOTO"
        lastPhoto = uri
        launchAnalysis {
            val bitmap = withContext(Dispatchers.IO) { decode(uri) }
            photo = bitmap
            preview = bitmap.asImageBitmap()
            val base64 = withContext(Dispatchers.Default) { Images.encode(bitmap) }
            gemini.analyzePhoto(base64, buildContext())
        }
    }

    fun analyzeText() {
        val text = description.trim()
        if (text.isEmpty()) return
        source = "TEXT"
        launchAnalysis { gemini.analyzeText(text, buildContext()) }
    }

    fun retry() {
        val photo = lastPhoto
        if (source == "PHOTO" && photo != null) analyzePhoto(photo) else analyzeText()
    }

    private fun launchAnalysis(block: suspend () -> MealAnalysis) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val result = block()
                analysis = result
                portion = 1.0
                edited = false
                correctionApplied = false
                originalName = result.mealName
                savedCorrection = if (result.isFood && result.mealName.isNotBlank()) {
                    runCatching { repo.getCorrection(result.mealName) }.getOrNull()
                } else null
            } catch (e: GeminiException) {
                error = e.message
            } catch (e: Exception) {
                error = "صار خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun scaleItem(index: Int, factor: Double) {
        val a = analysis ?: return
        val items = a.items.toMutableList()
        val item = items.getOrNull(index) ?: return
        val next = item.scaled(factor)
        if (next.calories <= 0 && factor < 1) return
        items[index] = next
        analysis = a.copy(items = items)
        edited = true
    }

    fun removeItem(index: Int) {
        val a = analysis ?: return
        analysis = a.copy(items = a.items.filterIndexed { i, _ -> i != index })
        edited = true
    }

    fun rename(name: String) {
        val a = analysis ?: return
        analysis = a.copy(mealName = name.take(60))
    }

    /** يغيّر عدد الحصص ويكبّر أو يصغّر كل المكونات بنفس النسبة. */
    fun setPortion(value: Double) {
        val a = analysis ?: return
        if (value <= 0 || value == portion) return
        val factor = value / portion
        analysis = a.copy(items = a.items.map { it.scaled(factor) })
        portion = value
    }

    /** يضيف مكوّن (من الأزرار السريعة أو يدوي) بحجم حصة وحدة. */
    fun addItem(item: FoodItem) {
        val a = analysis ?: return
        analysis = a.copy(items = a.items + item)
        edited = true
    }

    fun addCustom(name: String, amount: String, calories: Int) {
        if (name.isBlank() || calories <= 0) return
        addItem(FoodItem(name = name.trim(), portion = amount.trim(), grams = 0, calories = calories, fit = "MODERATE"))
    }

    /** يستبدل المكونات بتقدير المستخدم السابق لنفس الوجبة. */
    fun applyCorrection() {
        val a = analysis ?: return
        val saved = savedCorrection ?: return
        analysis = a.copy(items = saved.map { it.scaled(portion) })
        correctionApplied = true
        edited = false
    }

    fun save(onSaved: () -> Unit) {
        val a = analysis ?: return
        if (a.items.isEmpty()) return
        viewModelScope.launch {
            // نحفظ تصحيح المستخدم (لحصة وحدة) حتى نقترحه المرة الجاية لنفس الوجبة
            if (edited && source != "PLAN") {
                val perServing = a.items.map { it.scaled(1.0 / portion) }
                runCatching {
                    if (originalName.isNotBlank()) repo.saveCorrection(originalName, perServing)
                    if (a.mealName.isNotBlank() && a.mealName != originalName) repo.saveCorrection(a.mealName, perServing)
                }
            }
            val id = repo.addMeal(a, mealDay, mealMinute, source)
            if (source == "PHOTO") photo?.let { runCatching { thumbs.save(id, it) } }
            onSaved()
        }
    }

    private fun decode(uri: Uri): Bitmap = Images.decodeScaled(app, uri)
}
