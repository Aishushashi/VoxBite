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

    private val apiKey = "AIzaSyBmz05L8gPMh7Z2elxPM3C76cFfhXNtIw8" // ← paste new key here
    private val TAG = "VoxBite"

    suspend fun parseIntent(spokenText: String): UserIntent = withContext(Dispatchers.IO) {
        try {
            // ✅ FIXED: added -latest to fix 404 error
            // REPLACE the URL line with exactly this:
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val prompt = """
                You are an intent parser for a voice agent app.
                Parse this voice command and return ONLY valid JSON, no markdown, no explanation.
                Voice command: "$spokenText"

                Return exactly this format:
                {"action":"order","app":"swiggy","items":[{"name":"biryani","quantity":1}],"destination":null}

                Rules:
                - action: one of order, search, open, navigate
                - app: one of swiggy, ola, zomato
                - items: array of objects with name and quantity
                - destination: string for ola rides, null otherwise
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

            UserIntent(
                action = intentJson.optString("action", "unknown"),
                app = intentJson.optString("app", "unknown"),
                items = parsedItems,
                destination = intentJson.optString("destination", null).takeIf { it != "null" },
                rawText = spokenText
            )

        } catch (e: Exception) {
            Log.e(TAG, "IntentParser error: ${e.message}", e)
            UserIntent.error("Parse failed: ${e.message}")
        }
    }
}