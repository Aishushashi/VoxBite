package com.voxbite.app.agent

import android.util.Log
import com.voxbite.app.model.OrderItem
import com.voxbite.app.model.UserIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class IntentParser {

    private val apiKey = "AIzaSyCEiSh7a4YF-qlGc6Z137leIBUiig4_VDQ"
    private val TAG = "VoxBite"

    private val systemPrompt = """
You are VoxBite, a voice assistant for Indian users.
You understand English (en-IN), Hindi (hi-IN), and Kannada (kn-IN).

The user may speak in any of these languages or mix them.
You MUST detect which primary language they used and include it in your response.

Always respond with ONLY a valid JSON object in this exact format:
{
  "action": "order_food" | "book_cab" | "open_app" | "clarify" | "error",
  "items": [{"name": "item name in English", "quantity": 1}],
  "destination": "place name in English",
  "appName": "app name",
  "budget": 150,
  "category": "light" | "spicy" | null,
  "correction": false,
  "start_over": false,
  "detectedLanguage": "en-IN" | "hi-IN" | "kn-IN",
  "confirmationMessage": "Reply to user IN THE SAME LANGUAGE THEY SPOKE"
}

CRITICAL LANGUAGE RULES:
- If user spoke Hindi → detectedLanguage = "hi-IN" AND confirmationMessage must be in Hindi (Devanagari script)
- If user spoke Kannada → detectedLanguage = "kn-IN" AND confirmationMessage must be in Kannada (Kannada script)
- If user spoke English → detectedLanguage = "en-IN" AND confirmationMessage in English
- The confirmationMessage is what gets spoken aloud — always match the user's language.

Hindi confirmation examples:
- Food: "ठीक है, मैं Swiggy पर बिरयानी ऑर्डर कर रहा हूँ।"
- Cab: "ठीक है, Ola पर MG Road के लिए कैब बुक हो रही है।"
- Correction: "ठीक है, दोसा ऑर्डर करते हैं।"
- Start over: "ठीक है, फिर से शुरू करते हैं।"

Kannada confirmation examples:
- Food: "ಸರಿ, Swiggy ನಲ್ಲಿ ಬಿರಿಯಾನಿ ಆರ್ಡರ್ ಮಾಡುತ್ತಿದ್ದೇನೆ."
- Cab: "ಸರಿ, Ola ನಲ್ಲಿ MG Road ಗೆ ಕ್ಯಾಬ್ ಬುಕ್ ಆಗುತ್ತಿದೆ."
- Correction: "ಸರಿ, ದೋಸೆ ಆರ್ಡರ್ ಮಾಡೋಣ."
- Start over: "ಸರಿ, ಮತ್ತೆ ಶುರು ಮಾಡೋಣ."

Hindi start_over triggers: chodo, nahi chahiye, band karo, phir se, dobara
Kannada start_over triggers: ella bidru, beda, matte shuru maadi, cancel maadi

Always extract destination and item names in English regardless of input language.
Do NOT include any text outside the JSON object.
""".trimIndent()

    private val conversationHistory = mutableListOf<JSONObject>()

    var lastIntent: UserIntent? = null

    fun clearHistory() {
        conversationHistory.clear()
        lastIntent = null
        Log.d(TAG, "Conversation history cleared")
    }

    suspend fun parseIntent(spokenText: String): UserIntent = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val userMessage = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", spokenText))
                })
            }
            conversationHistory.add(userMessage)

            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })
                put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Understood. I will only return valid JSON with no explanation or markdown. I understand English, Hindi, and Kannada. I will always match the confirmationMessage language to the user's detected language."))
                    })
                })
                conversationHistory.forEach { put(it) }
            }

            val requestBody = JSONObject().apply {
                put("contents", contents)
            }.toString()

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = connection.responseCode
            Log.d(TAG, "Gemini response code: $responseCode")

            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e(TAG, "Gemini API error: $errorBody")
                conversationHistory.removeLastOrNull()
                return@withContext UserIntent.error("API error $responseCode: $errorBody")
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            Log.d(TAG, "Gemini raw response: $responseText")

            val content = JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Log.d(TAG, "Gemini parsed content: $content")

            val assistantMessage = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", content))
                })
            }
            conversationHistory.add(assistantMessage)

            while (conversationHistory.size > 20) {
                conversationHistory.removeAt(0)
            }

            val cleanJson = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val intentJson = JSONObject(cleanJson)

            // Handle start_over — clear history and return early
            if (intentJson.optBoolean("start_over", false)) {
                val lang = intentJson.optString("detectedLanguage", "en-IN")
                val msg = intentJson.optString("confirmationMessage", "Starting over.")
                clearHistory()
                return@withContext UserIntent(
                    action = "start_over",
                    app = "none",
                    rawText = spokenText,
                    confirmationMessage = msg,
                    detectedLanguage = lang
                )
            }

            // Parse items array
            val itemsArray = intentJson.optJSONArray("items")
            val parsedItems = mutableListOf<OrderItem>()
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    parsedItems.add(
                        OrderItem(
                            name = itemObj.optString("name", "unknown"),
                            quantity = itemObj.optInt("quantity", 1),
                            size = itemObj.optString("size", null)
                        )
                    )
                }
            }

            val isCorrection = intentJson.optBoolean("correction", false)

            val budget = if (intentJson.isNull("budget")) null
            else intentJson.optInt("budget", 0).takeIf { it > 0 }

            val category = if (intentJson.isNull("category")) null
            else intentJson.optString("category", null)

            val resolvedApp = intentJson.optString("app", "unknown").let {
                if (it == "unknown" && isCorrection) lastIntent?.app ?: it else it
            }

            // NEW: read detectedLanguage and confirmationMessage from Gemini
            val detectedLanguage = intentJson.optString("detectedLanguage", "en-IN")
            val confirmationMessage = intentJson.optString("confirmationMessage", "")

            val result = UserIntent(
                action = intentJson.optString("action", "unknown"),
                app = resolvedApp,
                items = parsedItems,
                destination = intentJson.optString("destination", null).takeIf { it != "null" },
                rawText = spokenText,
                budget = budget,
                category = category,
                isCorrection = isCorrection,
                detectedLanguage = detectedLanguage,       // NEW
                confirmationMessage = confirmationMessage  // NEW
            )

            lastIntent = result
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "IntentParser error: ${e.message}", e)
            conversationHistory.removeLastOrNull()
            UserIntent.error("Parse failed: ${e.message}")
        }
    }
}