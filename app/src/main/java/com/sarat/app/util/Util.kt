package com.sarat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object Images {
    private const val MAX_SIDE = 960

    /** ينشئ ملفاً مؤقتاً للكاميرا ويرجع Uri يمكن تمريره لـ TakePicture. */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** يفك الصورة مصغّرة إلى 1024 بكسل كحد أقصى (ImageDecoder يطبق اتجاه EXIF). يُستدعى خارج الخيط الرئيسي. */
    fun decodeScaled(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = MAX_SIDE.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** يحوّل الصورة إلى JPEG بصيغة Base64 لإرسالها إلى Gemini. */
    fun encode(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

object Dates {
    private val arabic = Locale.forLanguageTag("ar-IQ")
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", arabic)
    private val shortDayFormat = DateTimeFormatter.ofPattern("EEEE d MMM", arabic)
    private val fullDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", arabic)

    fun todayEpoch(): Long = LocalDate.now().toEpochDay()
    fun nowMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /** يصدر تاريخ اليوم ويتحدث تلقائياً بعد منتصف الليل. */
    fun todayFlow(): Flow<Long> = flow {
        while (true) {
            emit(todayEpoch())
            delay(30_000)
        }
    }.distinctUntilChanged()

    fun longDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormat)
    fun shortDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDayFormat)
    fun fullDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(fullDateFormat)

    fun time(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        val suffix = if (h < 12) "ص" else "م"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }
}

/** تنسيق أرقام عشرية بدون أصفار زائدة: 88.0 → "88" و 88.9 → "88.9". */
fun Double.fmt(decimals: Int = 1): String {
    val s = String.format(Locale.US, "%.${decimals}f", this)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
