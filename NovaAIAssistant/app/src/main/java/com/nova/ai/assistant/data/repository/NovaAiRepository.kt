package com.nova.ai.assistant.data.repository

import com.nova.ai.assistant.domain.model.NovaAction
import com.nova.ai.assistant.domain.model.SettingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiResponse(
    val replyText: String,
    val executedAction: NovaAction? = null
)

interface NovaAiRepository {
    suspend fun processUserQuery(
        userInput: String,
        userMemories: Map<String, String>
    ): Result<AiResponse>
}

class GeminiNovaAiRepository(private val apiKey: String) : NovaAiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun processUserQuery(
        userInput: String,
        userMemories: Map<String, String>
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("لم يتم تعيين مفتاح GEMINI_API_KEY داخل ملف local.properties")
            )
        }

        try {
            val memoryContext = userMemories.entries.joinToString("; ") { "\${it.key}: \${it.value}" }
            val systemInstruction = """
                أنت NOVA AI، مساعد شخصي صوتي فائق الذكاء مستوحى من Apple Siri. لست تطبيق دردشة نصي، بل مساعد صوتي تنفيذي سريع ودقيق.
                تتحدث باللغة العربية (بلهجة مصرية مهذبة أو فصحى راقية وموجزة) وبالإنجليزية عند الحاجة.
                سياق ذاكرة المستخدم: [\$memoryContext].
                
                قواعد الإجابة:
                1. الإيجاز البليغ: تحدث بنبرة طبيعية، مباشرة، دون مقدمات طويلة.
                2. استخرج النية وحولها إلى فعل في خاصية "action":
                   - فتح تطبيق: OPEN_APP:اسم_التطبيق
                   - البطارية: QUERY_BATTERY
                   - منبه أو تذكير: SET_ALARM:ساعة,دقيقة,الوصف
                   - تقويم: CREATE_CALENDAR:العنوان,ساعة,دقيقة
                   - قراءة إشعارات: READ_NOTIFICATIONS
                   - وضع عدم الإزعاج: TOGGLE_DND
                   - إعدادات: OPEN_SETTINGS:WIFI أو BLUETOOTH أو SOUND أو DISPLAY
                   - حفظ بالذاكرة: SAVE_MEMORY:المفتاح:القيمة
                   - بحث ويب: SEARCH_WEB:الاستعلام
                   - اتصال: PREPARE_CALL:الاسم:الرقم
                
                أرجع كائن JSON حصراً:
                {
                   "reply": "نص الرد الصوتي الموجز",
                   "action": "كود_الأمر_إن_وجد أو null"
                }
            """.trimIndent()

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userInput)))
                })
            }

            val jsonBody = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                })
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.3)
                })
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=\$apiKey"
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val rawBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطأ في استجابة المساعد الذكي: \${response.code} \$rawBody"))
            }

            val rootJson = JSONObject(rawBody)
            val candidateText = rootJson.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            var cleanJson = candidateText.trim()
            if (cleanJson.startsWith("```json")) cleanJson = cleanJson.removePrefix("```json")
            if (cleanJson.startsWith("```")) cleanJson = cleanJson.removePrefix("```")
            if (cleanJson.endsWith("```")) cleanJson = cleanJson.removeSuffix("```")
            cleanJson = cleanJson.trim()

            val parsedOutput = JSONObject(cleanJson)
            val replyText = parsedOutput.optString("reply", "أنا في خدمتك!")
            val actionString = parsedOutput.optString("action", "")

            val parsedAction = parseActionCommand(actionString)

            Result.success(AiResponse(replyText = replyText, executedAction = parsedAction))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseActionCommand(rawAction: String): NovaAction? {
        if (rawAction.isBlank() || rawAction == "null") return null
        val parts = rawAction.split(":")
        val command = parts[0].trim()

        return when (command) {
            "OPEN_APP" -> NovaAction.OpenApp(parts.getOrNull(1)?.trim().orEmpty())
            "OPEN_URL" -> NovaAction.OpenUrl(parts.getOrNull(1)?.trim().orEmpty())
            "QUERY_BATTERY" -> NovaAction.QueryBattery()
            "READ_NOTIFICATIONS" -> NovaAction.ReadNotifications()
            "TOGGLE_DND" -> NovaAction.ToggleDnd(true)
            "CREATE_CALENDAR" -> {
                val params = parts.getOrNull(1)?.split(",") ?: emptyList()
                val title = params.getOrNull(0)?.trim() ?: "موعد هام"
                val hour = params.getOrNull(1)?.trim()?.toIntOrNull() ?: 10
                val min = params.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                NovaAction.CreateCalendarEvent(title, "غداً", hour, min)
            }
            "SET_ALARM" -> {
                val params = parts.getOrNull(1)?.split(",") ?: emptyList()
                val hour = params.getOrNull(0)?.trim()?.toIntOrNull() ?: 8
                val min = params.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val label = params.getOrNull(2)?.trim() ?: "تنبيه من نوفا"
                NovaAction.SetAlarm(hour, min, label)
            }
            "OPEN_SETTINGS" -> {
                val type = when (parts.getOrNull(1)?.trim()?.uppercase()) {
                    "WIFI" -> SettingType.WIFI
                    "BLUETOOTH" -> SettingType.BLUETOOTH
                    "SOUND" -> SettingType.SOUND
                    "DISPLAY" -> SettingType.DISPLAY
                    else -> SettingType.GENERAL
                }
                NovaAction.OpenSettings(type)
            }
            "SAVE_MEMORY" -> {
                val key = parts.getOrNull(1)?.trim().orEmpty()
                val value = parts.getOrNull(2)?.trim().orEmpty()
                NovaAction.SaveMemory(key, value)
            }
            "PREPARE_CALL" -> {
                val name = parts.getOrNull(1)?.trim().orEmpty()
                val phone = parts.getOrNull(2)?.trim().orEmpty()
                NovaAction.PrepareCall(phone, name)
            }
            "SEARCH_WEB" -> NovaAction.WebSearch(parts.getOrNull(1)?.trim().orEmpty())
            else -> null
        }
    }
}
