package com.sarat.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "''") val name: String = "",
    val sex: String,
    val age: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val activity: String,
    /** أسماء الأهداف مفصولة بفاصلة. */
    val goals: String,
    val targetWeightKg: Double,
    val weeklyRateKg: Double,
    val startEpochDay: Long,
    val tdeeAdjustment: Int,
    val thyroidMedMinute: Int?,
)

@Entity(tableName = "meals", indices = [Index("epochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** تاريخ الوجبة مثبّت وقت التسجيل. */
    val epochDay: Long,
    val minuteOfDay: Int,
    val title: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    /** GOOD | OK_WITH_CHANGES | NOT_SUITABLE */
    val verdict: String,
    val verdictReason: String,
    val suggestion: String,
    /** المكونات بصيغة JSON. */
    val itemsJson: String,
    /** PHOTO | TEXT | PLAN */
    val source: String,
    /** المستخدم ثبّتها كوجبة مفضلة. */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** للوجبات المقترحة: شبعت؟ (1 نعم، 0 لا، null ما قيّم). */
    val ratedFull: Int? = null,
    /** للوجبات المقترحة: عجبتك؟ */
    val ratedLiked: Int? = null,
)

@Entity(tableName = "weights", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val kg: Double,
)

data class DayTotal(
    @ColumnInfo(name = "epochDay") val epochDay: Long,
    @ColumnInfo(name = "total") val total: Int,
)

/** تصحيحات المستخدم لوجبة معيّنة (لحصة وحدة)، حتى نقترحها المرة الجاية. */
@Entity(tableName = "corrections")
data class CorrectionEntity(
    @PrimaryKey val dishKey: String,
    val title: String,
    val itemsJson: String,
    val updatedAt: Long,
)

/** وصفة محفوظة: مقترحة من Gemini أو مضافة يدوياً. [json] = MealSuggestion. */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val json: String,
    /** AI | USER */
    val source: String,
    val createdAt: Long,
)

/** عنصر بقائمة المشتريات. */
@Entity(tableName = "shopping")
data class ShoppingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: String,
    val done: Boolean,
    val createdAt: Long,
)
