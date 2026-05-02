package com.voxbite.app.agent

import com.google.gson.Gson
import com.voxbite.app.model.UserIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class IntentParser {

    private val client = OkHttpClient()
    private val gson = Gson()

    // 🔑 Paste your Gemini API key here
    private val API_KEY = "YOUR_GEMINI_API_KEY_HERE"
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-1.5-flash:generateContent?key=$API_KEY"

    suspend fun parse(userSpeech: String): UserIntent = withContext(Dispatchers.IO) {

        val prompt = """
            You are an intent parser for VoxBite, a voice agent app in India.
            The user spoke: "$userSpeech"
            
            Extract the intent and return ONLY a JSON object:
            {
              "action": "order_food" or "book_cab" or "search_product" or "unknown",
              "app": "swiggy" or "zomato" or "zepto" or "ola" or "uber" or "unknown",
              "items": [{"name": "item name", "quantity": 1, "size": null}],
              "destination": null or "place name if cab booking",
              "rawText": "$userSpeech"
            }
            
            Rules:
            - No specific app mentioned → pick most logical one
            - Food orders → default swiggy
            - Grocery → default zepto
            - Cab → default ola
            - Return ONLY the JSON, no explanation, no markdown
        """.trimIndent()

        val requestBody = """
            {
              "contents": [{
                "parts": [{"text": "${prompt.replace("\"", "\\\"")}"}]
              }]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseText = response.body?.string() ?: ""

            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            val candidates = jsonResponse["candidates"] as? List<*>
            val content = (candidates?.firstOrNull() as? Map<*, *>)?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val text = (parts?.firstOrNull() as? Map<*, *>)?.get("text") as? String ?: ""

            val cleanJson = text.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            gson.fromJson(cleanJson, UserIntent::class.java)

        } catch (e: Exception) {
            UserIntent(action = "unknown", app = "unknown", rawText = userSpeech)
        }
    }
}

