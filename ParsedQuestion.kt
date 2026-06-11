package com.example.answerlens.models

import org.json.JSONArray
import org.json.JSONObject

data class ParsedQuestion(
    val question: String,
    val choices: List<String>,
    val type: String,
    val topic: String,
    val rawText: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("question", question)
        put("choices", JSONArray(choices))
        put("type", type)
        put("topic", topic)
        put("raw_text", rawText)
    }
}
