package com.voxbite.app.agent

import android.util.Log
import com.voxbite.app.model.OrderItem
import com.voxbite.app.model.UserIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class IntentParser {

    private val apiKey = "AIzaSyBmz05L8gPMh7Z2elxPM3C76cFfhXNtIw8" // ← your key here
    private val TAG = "VoxBite"

    suspend fun parseIntent(spokenText: String): UserIntent = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val prompt = """
                You are an intent parser for a voice agent app in India.
                Parse this voice command and return ONLY valid JSON, no markdown, no explanation.
                Voice command: "$spokenText"

                Return exactly this format:
                {
                  "action": "order",
                  "app": "swiggy",
                  "items": [{"name": "biryani", "quantity": 1}],
                  "destination": null,
                  "budget": null,
                  "category": null
                }

                Rules:
                - action: one of order, book_cab, open, navigate
                - app: one of swiggy, ola, zomato
                - items: array of objects with name and quantity. Extract ALL items mentioned.
                - destination: string only for cab bookings, null otherwise
                - budget: number in rupees if user says "under ₹150" or "below 200" etc, null if not mentioned
                - category: if user says "something light" → "light", "something spicy" → "spicy", null otherwise
                - If user says "something light under ₹150", set items to [] and use category + budget instead
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
            }.toString()

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = connection.responseCode
            Log.d(TAG, "Gemini response code: $responseCode")

            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e(TAG, "Gemini API error: $errorBody")
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

            val cleanJson = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val intentJson = JSONObject(cleanJson)

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

            val budget = if (intentJson.isNull("budget")) null
            else intentJson.optInt("budget", 0).takeIf { it > 0 }

            val category = if (intentJson.isNull("category")) null
            else intentJson.optString("category", null)

            UserIntent(
                action = intentJson.optString("action", "unknown"),
                app = intentJson.optString("app", "unknown"),
                items = parsedItems,
                destination = intentJson.optString("destination", null).takeIf { it != "null" },
                rawText = spokenText,
                budget = budget,
                category = category
            )

        } catch (e: Exception) {
            Log.e(TAG, "IntentParser error: ${e.message}", e)
            UserIntent.error("Parse failed: ${e.message}")
        }
    }
}