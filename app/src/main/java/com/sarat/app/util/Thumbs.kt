package com.sarat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/** صور مصغّرة للوجبات (256 بكسل) محفوظة على الموبايل باسم رقم الوجبة. */
class Thumbs(context: Context) {
    private val dir = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun file(mealId: Long) = File(dir, "$mealId.jpg")

    suspend fun save(mealId: Long, source: Bitmap) = withContext(Dispatchers.IO) {
        // قص مربع من الوسط ثم تصغير
        val side = min(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, 256, 256, true)
        file(mealId).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    suspend fun load(mealId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(mealId)
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** نسخ صورة وجبة لوجبة جديدة (لما تتسجل من المفضلة). */
    suspend fun copy(fromId: Long, toId: Long) = withContext(Dispatchers.IO) {
        val src = file(fromId)
        if (src.exists()) src.copyTo(file(toId), overwrite = true)
        Unit
    }

    fun delete(mealId: Long) {
        file(mealId).delete()
    }
}
