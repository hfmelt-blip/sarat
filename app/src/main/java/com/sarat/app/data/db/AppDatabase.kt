package com.sarat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, MealEntity::class, WeightEntity::class, CorrectionEntity::class, RecipeEntity::class, ShoppingEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingDao(): ShoppingDao

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

        /** النسخة ٥: الوصفات المحفوظة وقائمة المشتريات. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, json TEXT NOT NULL, source TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS shopping (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, amount TEXT NOT NULL, done INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sarat.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
