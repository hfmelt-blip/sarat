package com.sarat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profile SET tdeeAdjustment = :adjustment WHERE id = 1")
    suspend fun setAdjustment(adjustment: Int)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE epochDay = :day ORDER BY minuteOfDay")
    fun observeDay(day: Long): Flow<List<MealEntity>>

    @Query("SELECT epochDay, SUM(calories) AS total FROM meals GROUP BY epochDay ORDER BY epochDay DESC")
    fun observeDailyTotals(): Flow<List<DayTotal>>

    @Query("SELECT epochDay, SUM(calories) AS total FROM meals WHERE epochDay >= :from AND epochDay < :until GROUP BY epochDay")
    suspend fun totalsBetween(from: Long, until: Long): List<DayTotal>

    /**
     * الوجبات المفضلة: اللي ثبّتها المستخدم، أو اللي تكررت مرتين أو أكثر بنفس الاسم.
     * نرجع آخر نسخة من كل وجبة، والأكثر تكراراً أولاً.
     */
    @Query(
        """
        SELECT m.* FROM meals m
        JOIN (SELECT title, MAX(id) AS mid, COUNT(*) AS c, MAX(favorite) AS fav FROM meals GROUP BY title) f
          ON m.id = f.mid
        WHERE f.c >= 2 OR f.fav = 1
        ORDER BY f.fav DESC, f.c DESC, m.id DESC
        LIMIT 10
        """
    )
    fun observeFavorites(): Flow<List<MealEntity>>

    @Query("UPDATE meals SET ratedFull = :full, ratedLiked = :liked WHERE id = :id")
    suspend fun setRating(id: Long, full: Int?, liked: Int?)

    @Query("SELECT title, ratedFull, ratedLiked FROM meals WHERE source = 'PLAN' AND (ratedFull IS NOT NULL OR ratedLiked IS NOT NULL) ORDER BY id DESC LIMIT 60")
    suspend fun ratings(): List<RatingRow>

    @Query("UPDATE meals SET favorite = :value WHERE title = :title")
    suspend fun setFavorite(title: String, value: Boolean)

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: Long): MealEntity?

    @Insert
    suspend fun insert(meal: MealEntity): Long

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): WeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weight: WeightEntity)
}

@Dao
interface CorrectionDao {
    @Query("SELECT * FROM corrections WHERE dishKey = :key")
    suspend fun get(key: String): CorrectionEntity?

    @Upsert
    suspend fun upsert(c: CorrectionEntity)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE title = :title LIMIT 1")
    suspend fun findByTitle(title: String): RecipeEntity?

    @Insert
    suspend fun insert(r: RecipeEntity): Long

    @Upsert
    suspend fun upsert(r: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping ORDER BY done ASC, createdAt DESC")
    fun observeAll(): Flow<List<ShoppingEntity>>

    @Query("SELECT COUNT(*) FROM shopping WHERE name = :name AND done = 0")
    suspend fun countOpen(name: String): Int

    @Insert
    suspend fun insert(item: ShoppingEntity): Long

    @Query("UPDATE shopping SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM shopping WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shopping WHERE done = 1")
    suspend fun clearDone()
}

data class RatingRow(val title: String, val ratedFull: Int?, val ratedLiked: Int?)
