package com.sarat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, MealEntity::class, WeightEntity::class, CorrectionEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao
    abstract fun correctionDao(): CorrectionDao

    companion object {
        /** النسخة ٢: إضافة اسم المستخدم بدون ما تنمسح بيانات أحد. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            }
        }

        /** النسخة ٣: تثبيت الوجبات المفضلة. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meals ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** النسخة ٤: تصحيحات المستخدم للوجبات. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS corrections (dishKey TEXT NOT NULL, title TEXT NOT NULL, " +
                        "itemsJson TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(dishKey))"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sarat.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
