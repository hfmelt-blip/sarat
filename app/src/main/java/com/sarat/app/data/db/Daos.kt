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
