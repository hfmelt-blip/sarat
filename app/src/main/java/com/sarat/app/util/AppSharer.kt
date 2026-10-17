package com.sarat.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * يشارك ملف التطبيق نفسه (APK) عبر قائمة المشاركة بأندرويد:
 * Quick Share / Wi-Fi Direct أو البلوتوث أو أي تطبيق مراسلة.
 * كل صديق يثبّت التطبيق ويربط مفتاح Gemini المجاني الخاص بيه.
 */
object AppSharer {
    private const val MIME_APK = "application/vnd.android.package-archive"

    suspend fun share(context: Context) {
        val apk = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "Sarat.apk")
            File(context.applicationInfo.sourceDir).copyTo(out, overwrite = true)
            out
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_APK
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "تطبيق سعرات")
            putExtra(
                Intent.EXTRA_TEXT,
                "جرّب تطبيق «سعرات»: صوّر وجبتك ويحسب سعراتها ويقيّمها حسب هدفك. " +
                    "ثبّت الملف المرفق، وإذا طلب الموبايل وافق على التثبيت من هذا المصدر.",
            )
            clipData = ClipData.newRawUri("Sarat.apk", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "شارك التطبيق عبر").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
