package com.sarat.app.data

import android.content.Context
import com.sarat.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * يحفظ مفتاح Gemini على الموبايل نفسه، فالمستخدم يلصقه داخل التطبيق
 * وما يحتاج يعدّل الكود أو يعيد البناء.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _key = MutableStateFlow(prefs.getString(KEY, null).orEmpty().ifBlank { BuildConfig.GEMINI_API_KEY })
    val key: StateFlow<String> = _key.asStateFlow()

    val current: String get() = _key.value
    val hasKey: Boolean get() = _key.value.isNotBlank()

    fun save(value: String) {
        val clean = value.trim()
        prefs.edit().putString(KEY, clean).apply()
        _key.value = clean
    }

    private companion object {
        const val KEY = "gemini_api_key"
    }
}
