package com.sarat.app.data.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiException(message: String) : Exception(message)

/**
 * يستدعي Gemini API مباشرة عبر REST ويطلب رداً بصيغة JSON مطابقة لـ [MealAnalysis].
 *
 * المفتاح يلصقه المستخدم داخل التطبيق ويُحفظ على موبايله فقط.
 * يشتغل مع الخطة المجانية لـ Gemini.
 */
class GeminiClient(
    private val keyProvider: () -> String,
    /** النماذج بالترتيب: إذا انتهت حصة الأول المجانية ننتقل للي بعده. */
    private val models: List<String>,
) {
    companion object {
        /** نموذج احتياطي خفيف ضمن الخطة المجانية، حصته اليومية أكبر. */
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    }

    private class HttpError(val code: Int, message: String) : Exception(message)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val isConfigured: Boolean get() = keyProvider().isNotBlank()

    suspend fun analyzePhoto(jpegBase64: String, ctx: MealContext): MealAnalysis =
        generate(Prompts.userText(ctx, null), jpegBase64)

    suspend fun analyzeText(description: String, ctx: MealContext): MealAnalysis =
        generate(Prompts.userText(ctx, description), null)

    /** يتأكد إن المفتاح شغال قبل حفظه. يرجع null إذا تمام، أو رسالة الخطأ. */
    suspend fun verifyKey(key: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models?pageSize=1")
            .header("x-goog-api-key", key.trim())
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> null
                    response.code == 400 || response.code == 401 || response.code == 403 ->
                        "المفتاح غير صحيح. تأكد إنك نسخته كامل."
                    else -> "ما قدرنا نتحقق من المفتاح (${response.code}). حاول بعد شوية."
                }
            }
        } catch (e: IOException) {
            "تعذّر الاتصال. تأكد من الإنترنت."
        }
    }

    private suspend fun generate(text: String, imageBase64: String?): MealAnalysis {
        val answer = requestJson(Prompts.system, text, imageBase64, responseSchema)
        return try {
            json.decodeFromString(MealAnalysis.serializer(), answer)
        } catch (e: Exception) {
            throw GeminiException("ما قدرنا نفهم رد التحليل. حاول مرة ثانية.")
        }
    }

    /** يقترح وجبة من المكونات المتوفرة ضمن سعرات محددة. */
    suspend fun suggestMeal(userText: String): MealSuggestion {
        val answer = requestJson(PlanPrompts.system, userText, null, PlanPrompts.schema)
        return try {
            json.decodeFromString(MealSuggestion.serializer(), answer)
        } catch (e: Exception) {
            throw GeminiException("ما قدرنا نفهم الاقتراح. حاول مرة ثانية.")
        }
    }

    /** يرسل الطلب ويرجع نص JSON من الرد، وينتقل للنموذج الاحتياطي إذا خلصت الحصة. */
    private suspend fun requestJson(
        system: String,
        text: String,
        imageBase64: String?,
        schema: JsonObject,
    ): String = withContext(Dispatchers.IO) {
        val key = keyProvider()
        if (key.isBlank()) throw GeminiException("اربط Gemini أولاً: الصق مفتاحك المجاني من شاشة الإعدادات.")

        fun body(lowThinking: Boolean) = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        if (imageBase64 != null) {
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", imageBase64)
                                }
                            }
                        }
                        addJsonObject { put("text", text) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
                // تفكير أقل = رد أسرع بكثير، والمهمة ما تحتاج تفكير طويل
                if (lowThinking) putJsonObject("thinkingConfig") { put("thinkingLevel", "low") }
            }
        }.toString()

        var lastError: HttpError? = null
        for (model in models) {
            val raw = try {
                try {
                    call(model, key, body(lowThinking = true))
                } catch (e: HttpError) {
                    // إذا النموذج ما يقبل إعداد التفكير، نعيد الطلب بدونه
                    if (e.code == 400) call(model, key, body(lowThinking = false)) else throw e
                }
            } catch (e: HttpError) {
                lastError = e
                // نجرب النموذج الاحتياطي إذا المشكلة بالحصة أو النموذج أو ضغط الخدمة أو التأخير
                if (e.code == 429 || e.code == 404 || e.code == 408 || e.code >= 500) continue
                throw GeminiException(e.message ?: "خطأ من Gemini")
            }
            return@withContext extractText(raw)
        }
        throw GeminiException(lastError?.message ?: "خدمة Gemini غير متاحة حالياً.")
    }

    private fun call(model: String, key: String, body: String): String {
        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HttpError(response.code, errorMessage(response.code, model, payload))
                payload
            }
        } catch (e: java.net.SocketTimeoutException) {
            throw HttpError(408, "Gemini طوّل بالرد. حاول مرة ثانية.")
        } catch (e: java.io.InterruptedIOException) {
            throw HttpError(408, "Gemini طوّل بالرد. حاول مرة ثانية.")
        } catch (e: java.net.UnknownHostException) {
            throw GeminiException("ما كو اتصال بالإنترنت. تأكد من الشبكة وحاول مرة ثانية.")
        } catch (e: IOException) {
            throw HttpError(503, "تعذّر الوصول لخدمة Gemini. حاول بعد شوية.")
        }
    }

    private fun extractText(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw GeminiException("ما وصل رد من التحليل. جرّب صورة أوضح أو وصفاً أدق.")
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
        val text = parts
            .map { it.jsonObject }
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (text.isEmpty()) throw GeminiException("الرد كان فارغاً. حاول مرة ثانية.")
        return text
    }

    private fun errorMessage(code: Int, model: String, payload: String): String = when (code) {
        400 -> "الطلب مرفوض من Gemini (400)."
        401, 403 -> "مفتاح Gemini غير صالح. غيّره من الإعدادات."
        404 -> "النموذج $model غير متاح."
        429 -> "خلصت الحصة المجانية لهذي الدقيقة أو اليوم. انتظر شوية وحاول."
        in 500..599 -> "خدمة Gemini مشغولة حالياً. حاول بعد قليل."
        else -> "خطأ من Gemini ($code)."
    }.also { android.util.Log.w("GeminiClient", "$model HTTP $code: ${payload.take(500)}") }

    private val responseSchema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("isFood") { put("type", "BOOLEAN") }
            putJsonObject("mealName") { put("type", "STRING") }
            putJsonObject("items") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "STRING") }
                        putJsonObject("portion") { put("type", "STRING") }
                        putJsonObject("grams") { put("type", "INTEGER") }
                        putJsonObject("calories") { put("type", "INTEGER") }
                        putJsonObject("proteinG") { put("type", "NUMBER") }
                        putJsonObject("carbsG") { put("type", "NUMBER") }
                        putJsonObject("fatG") { put("type", "NUMBER") }
                        putJsonObject("fit") {
                            put("type", "STRING")
                            put("format", "enum")
                            putJsonArray("enum") { add("GOOD"); add("MODERATE"); add("AVOID") }
                        }
                    }
                    putJsonArray("required") {
                        add("name"); add("portion"); add("grams"); add("calories")
                        add("proteinG"); add("carbsG"); add("fatG"); add("fit")
                    }
                }
            }
            putJsonObject("verdict") {
                put("type", "STRING")
                put("format", "enum")
                putJsonArray("enum") { add("GOOD"); add("OK_WITH_CHANGES"); add("NOT_SUITABLE") }
            }
            putJsonObject("verdictReason") { put("type", "STRING") }
            putJsonObject("suggestion") { put("type", "STRING") }
            putJsonObject("medicationNote") { put("type", "STRING") }
        }
        putJsonArray("required") {
            add("isFood"); add("mealName"); add("items"); add("verdict")
            add("verdictReason"); add("suggestion"); add("medicationNote")
        }
    }
}
