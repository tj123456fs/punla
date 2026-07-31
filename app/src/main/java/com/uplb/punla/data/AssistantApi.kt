package com.uplb.punla.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface AssistantApiResult {
    data class Success(val text: String) : AssistantApiResult
    data class Failure(val message: String) : AssistantApiResult
}

/** Optional personal-only Claude Messages API escalation path. */
object AssistantApi {
    suspend fun ask(
        apiKey: String,
        model: String,
        userQuery: String,
        compactContext: String
    ): AssistantApiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AssistantApiResult.Failure("Add an API key in Settings first.")
        runCatching {
            val connection = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            val payload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 500)
                put(
                    "system",
                    JSONArray().put(JSONObject().apply {
                        put("type", "text")
                        put(
                            "text",
                            "You are Punla's concise student-planning assistant. Use only the supplied local context. " +
                                "Never invent schedule, deadline, attendance, spending, or study facts. State when data is missing."
                        )
                        // Repeated static prefix; Anthropic may cache it when
                        // the configured model's minimum cacheable size is met.
                        put("cache_control", JSONObject().put("type", "ephemeral"))
                    })
                )
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", "LOCAL CONTEXT:\n$compactContext\n\nQUESTION:\n$userQuery")
                }))
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                AssistantApiResult.Failure(
                    runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "Assistant request failed ($status)."
                )
            } else {
                val content = JSONObject(body).optJSONArray("content")
                val text = buildString {
                    if (content != null) for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i)
                        if (block?.optString("type") == "text") append(block.optString("text"))
                    }
                }.trim()
                if (text.isBlank()) AssistantApiResult.Failure("The assistant returned an empty response.")
                else AssistantApiResult.Success(text)
            }
        }.getOrElse { AssistantApiResult.Failure(it.message ?: "Couldn't reach the assistant right now.") }
    }
}
