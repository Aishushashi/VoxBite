package com.voxbite.app.agent

import android.util.Log
import com.voxbite.app.BuildConfig
import com.voxbite.app.model.OrderItem
import com.voxbite.app.model.UserIntent
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class IntentParser {

    private val TAG      = "VoxBiteParser"
    private val apiKey   = BuildConfig.GEMINI_API_KEY
    private val model    = "gemini-2.5-flash"
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    private val history = mutableListOf<Map<String, Any>>()

    private val systemPrompt = """
You are VoxBite, a voice assistant that controls apps on an Android phone.
The user may speak in English, Hindi, or Kannada. Detect which language they used.

You must ALWAYS respond with a JSON object and nothing else. No explanation, no markdown, no backticks.

IMPORTANT: If the user says BOTH a food order AND a cab booking in ONE sentence, use action = "chain".
IMPORTANT: If the user says any emergency or distress word, use action = "emergency".

JSON format for single action:
{
  "action": "order_food" | "book_cab" | "open" | "start_over" | "emergency" | "error",
  "app": "swiggy" | "ola" | "zomato" | "",
  "items": [{"name": "item name in English", "quantity": 1}],
  "destination": "place name in English or null",
  "budget": number or null,
  "category": "light" | "heavy" | "spicy" | "sweet" | null,
  "confirmationMessage": "YOUR REPLY — MUST BE IN THE SAME LANGUAGE THE USER SPOKE",
  "detectedLanguage": "en-IN" | "hi-IN" | "kn-IN",
  "isCorrection": true | false,
  "chain": null
}

JSON format for CHAINED action (food + cab in one sentence):
{
  "action": "chain",
  "app": "",
  "items": [],
  "destination": null,
  "budget": null,
  "category": null,
  "confirmationMessage": "YOUR REPLY confirming BOTH actions — MUST BE IN THE SAME LANGUAGE THE USER SPOKE",
  "detectedLanguage": "en-IN" | "hi-IN" | "kn-IN",
  "isCorrection": false,
  "chain": {
    "food": {
      "action": "order_food",
      "app": "swiggy",
      "items": [{"name": "item name in English", "quantity": 1}],
      "budget": null,
      "category": null
    },
    "cab": {
      "action": "book_cab",
      "app": "ola",
      "destination": "place name in English"
    }
  }
}

Emergency trigger words (ANY of these → action = "emergency"):
- English: emergency, help me, help, SOS, ambulance, hospital, accident, danger
- Hindi:   bachao, madad karo, bachao mujhe, ambulance bulao, khatra, help karo
- Kannada: help maadi, nodikoli, ambulance kareyiri, apaaya, sahaya maadi

Emergency JSON example:
{
  "action": "emergency",
  "app": "",
  "items": [],
  "destination": null,
  "budget": null,
  "category": null,
  "confirmationMessage": "ठीक है, आपकी लोकेशन आपके संपर्कों को भेज रहा हूँ।",
  "detectedLanguage": "hi-IN",
  "isCorrection": false,
  "chain": null
}

Chain trigger examples:
- "biryani order karo aur Koramangala ke liye cab book karo"  → action = "chain"
- "order biryani and book cab to Koramangala"                 → action = "chain"
- "biryani order maadi mattu Koramangala ge cab bekku"        → action = "chain"
- "ghar ke liye cab book karo aur biryani order kar do"       → action = "chain"

Chain confirmationMessage examples:
- Hindi:   "ठीक है! बिरयानी ऑर्डर कर रहा हूँ और कोरमंगला के लिए ओला बुक कर रहा हूँ।"
- Kannada: "ಸರಿ! ಬಿರಿಯಾನಿ ಆರ್ಡರ್ ಮಾಡುತ್ತಿದ್ದೇನೆ ಮತ್ತು ಕೊರಮಂಗಲಕ್ಕೆ ಓಲಾ ಬುಕ್ ಮಾಡುತ್ತಿದ್ದೇನೆ."
- English: "Sure! Ordering biryani on Swiggy and booking a cab to Koramangala on Ola."

CRITICAL RULE — confirmationMessage language:
- User spoke Hindi   → confirmationMessage MUST be in Hindi (Devanagari script)
- User spoke Kannada → confirmationMessage MUST be in Kannada (Kannada script)
- User spoke English → confirmationMessage in English

Hindi trigger words: order karo, cab book karo, halka, teekha, nahi, chodo, ke liye, mujhe, aur
Kannada trigger words: order maadi, ge cab bekku, teredu, ella bidru, bekku, maadi, mattu

Hindi single confirmationMessage examples:
- "biryani order karo"               → "ठीक है, स्विगी पर बिरयानी ऑर्डर कर रहा हूँ।"
- "Koramangala ke liye cab book karo"→ "ठीक है, कोरमंगला के लिए ओला बुक कर रहा हूँ।"
- "kuch halka order karo"            → "ठीक है, स्विगी पर हल्का खाना खोज रहा हूँ।"
- "nahi, dosa karo"                  → "ठीक है, बदल रहा हूँ — डोसा ऑर्डर करता हूँ।"
- "chodo"                            → "ठीक है, फिर से शुरू करते हैं। क्या करना है?"

Kannada single confirmationMessage examples:
- "biryani order maadi"              → "ಸರಿ, ಸ್ವಿಗ್ಗಿಯಲ್ಲಿ ಬಿರಿಯಾನಿ ಆರ್ಡರ್ ಮಾಡುತ್ತಿದ್ದೇನೆ."
- "Koramangala ge cab bekku"         → "ಸರಿ, ಕೊರಮಂಗಲಕ್ಕೆ ಓಲಾ ಬುಕ್ ಮಾಡುತ್ತಿದ್ದೇನೆ."
- "ella bidru"                       → "ಸರಿ, ಮತ್ತೆ ಶುರು ಮಾಡೋಣ. ಏನು ಮಾಡಬೇಕು?"

Other rules:
- item names always in English inside the items array
- destination always in English
- budget extracted as a number only (150 not "150 rupees")
- isCorrection = true if user is changing a previous request
- action = "start_over" when user says chodo / ella bidru / cancel / start over
- action = "error" for unknown input; confirmationMessage explains in user's language
- items array is empty [] for non-food actions
- chain field is null for all non-chain actions
""".trimIndent()

    // ─── Public parse function ────────────────────────────────────────────────

    suspend fun parseIntent(userSpeech: String): UserIntent {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                history.add(mapOf("role" to "user", "parts" to listOf(mapOf("text" to userSpeech))))
                if (history.size > 20) {
                    history.removeAt(0)
                    history.removeAt(0)
                }
                val responseText = callGemini()
                val intent       = parseJson(responseText)
                history.add(mapOf("role" to "model", "parts" to listOf(mapOf("text" to responseText))))
                intent
            } catch (e: Exception) {
                Log.e(TAG, "parseIntent failed: ${e.message}", e)
                if (history.isNotEmpty()) history.removeAt(history.size - 1)
                UserIntent.error(e.message ?: "Network or parse error")
            }
        }
    }

    fun clearHistory() { history.clear() }

    // ─── Gemini HTTP call ─────────────────────────────────────────────────────

    private fun callGemini(): String {
        val turns = mutableListOf<Map<String, Any>>()
        turns.add(mapOf("role" to "user",  "parts" to listOf(mapOf("text" to systemPrompt))))
        turns.add(mapOf("role" to "model", "parts" to listOf(mapOf("text" to "Understood. I will respond only in JSON."))))
        turns.addAll(history)

        val body = JSONObject().apply {
            put("contents", org.json.JSONArray(turns.map { turn ->
                JSONObject().apply {
                    put("role", turn["role"])
                    put("parts", org.json.JSONArray(
                        (turn["parts"] as List<*>).map { part ->
                            JSONObject().apply {
                                put("text", (part as Map<*, *>)["text"])
                            }
                        }
                    ))
                }
            }))
        }.toString()

        val url  = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout    = 15000

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val code     = conn.responseCode
        val response = if (code == 200) conn.inputStream.bufferedReader().readText()
        else             conn.errorStream.bufferedReader().readText()

        Log.d(TAG, "Gemini HTTP $code")
        if (code != 200) throw Exception("Gemini error $code: $response")

        val json = JSONObject(response)
        return json
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    // ─── JSON → UserIntent ────────────────────────────────────────────────────

    private fun parseJson(raw: String): UserIntent {
        Log.d(TAG, "Gemini raw: $raw")

        val clean = raw
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val j = JSONObject(clean)

        val itemsArray = j.optJSONArray("items")
        val items = mutableListOf<OrderItem>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                items.add(OrderItem(
                    name     = obj.optString("name", ""),
                    quantity = obj.optInt("quantity", 1)
                ))
            }
        }

        val chainJson = j.optJSONObject("chain")
        val chainData: UserIntent.ChainData? = if (chainJson != null) {
            val foodJson = chainJson.optJSONObject("food")
            val cabJson  = chainJson.optJSONObject("cab")

            val chainItems = mutableListOf<OrderItem>()
            foodJson?.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    chainItems.add(OrderItem(
                        name     = obj.optString("name", ""),
                        quantity = obj.optInt("quantity", 1)
                    ))
                }
            }

            UserIntent.ChainData(
                foodItems   = chainItems,
                foodApp     = foodJson?.optString("app", "swiggy") ?: "swiggy",
                destination = cabJson?.optString("destination", "") ?: "",
                cabApp      = cabJson?.optString("app", "ola") ?: "ola"
            )
        } else null

        return UserIntent(
            action              = j.optString("action", "error"),
            app                 = j.optString("app", ""),
            items               = items,
            destination         = j.optString("destination").takeIf { it.isNotBlank() && it != "null" },
            budget              = j.optInt("budget").takeIf { it > 0 },
            category            = j.optString("category").takeIf { it.isNotBlank() && it != "null" },
            confirmationMessage = j.optString("confirmationMessage", ""),
            detectedLanguage    = j.optString("detectedLanguage", "en-IN"),
            isCorrection        = j.optBoolean("isCorrection", false),
            chainData           = chainData
        )
    }
}