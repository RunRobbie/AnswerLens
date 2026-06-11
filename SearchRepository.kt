package com.example.answerlens

import android.content.Context
import com.example.answerlens.models.AnswerMode
import com.example.answerlens.models.AnswerResult
import com.example.answerlens.models.ParsedQuestion
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SearchRepository(private val context: Context) {
    fun requestAnswer(
        parsedQuestion: ParsedQuestion,
        mode: AnswerMode,
        callback: (AnswerResult?, String?) -> Unit
    ) {
        val endpoint = Prefs.apiEndpoint(context)
        val apiKey = Prefs.apiKey(context)
        if (endpoint.isBlank()) {
            callback(null, "No answer endpoint configured.")
            return
        }

        thread(name = "AnswerLensRemoteAnswer") {
            try {
                val prompt = buildPrompt(parsedQuestion)
                val payload = JSONObject().apply {
                    put("mode", mode.prefValue)
                    put("prompt", prompt)
                    put("question", parsedQuestion.toJson())
                }

                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                if (connection.responseCode !in 200..299) {
                    callback(null, "Remote endpoint returned HTTP ${connection.responseCode}: $body")
                    return@thread
                }

                val json = extractJsonObject(body)
                val resultObject = json.optJSONObject("result") ?: json
                callback(AnswerResult.fromJson(resultObject), null)
            } catch (e: Exception) {
                callback(null, e.message ?: "Remote answer request failed.")
            }
        }
    }

    private fun buildPrompt(parsedQuestion: ParsedQuestion): String = """
        You are helping with a personal study-guide app.

        Analyze the question and answer choices below. Identify the most likely answer and explain the concept clearly.

        Return JSON only.

        Required JSON fields:
        - likely_answer
        - explanation
        - confidence
        - study_tip
        - related_concepts

        Question:
        ${parsedQuestion.question}

        Answer choices:
        ${JSONArray(parsedQuestion.choices)}

        Question type:
        ${parsedQuestion.type}

        Detected topic:
        ${parsedQuestion.topic}
    """.trimIndent()

    private fun extractJsonObject(body: String): JSONObject {
        val trimmed = body.trim()
        return if (trimmed.startsWith("{")) {
            JSONObject(trimmed)
        } else {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) JSONObject(trimmed.substring(start, end + 1)) else JSONObject()
        }
    }
}
