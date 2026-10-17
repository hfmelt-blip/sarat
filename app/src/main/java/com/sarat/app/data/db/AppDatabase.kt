package com.sarat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, MealEntity::class, WeightEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao

    companion object {
        /** النسخة ٢: إضافة اسم المستخدم بدون ما تنمسح بيانات أحد. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sarat.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
