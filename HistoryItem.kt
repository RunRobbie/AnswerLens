package com.example.answerlens.models

import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val question: String,
    val answer: String,
    val explanation: String,
    val timestampMillis: Long,
    val topic: String,
    val type: String,
    val confidence: Double,
    val studyTip: String,
    val relatedConcepts: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("question", question)
        put("answer", answer)
        put("explanation", explanation)
        put("timestamp_millis", timestampMillis)
        put("topic", topic)
        put("type", type)
        put("confidence", confidence)
        put("study_tip", studyTip)
        put("related_concepts", JSONArray(relatedConcepts))
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryItem {
            val concepts = mutableListOf<String>()
            val arr = json.optJSONArray("related_concepts") ?: JSONArray()
            for (i in 0 until arr.length()) concepts.add(arr.optString(i))
            return HistoryItem(
                question = json.optString("question"),
                answer = json.optString("answer"),
                explanation = json.optString("explanation"),
                timestampMillis = json.optLong("timestamp_millis"),
                topic = json.optString("topic", "General"),
                type = json.optString("type", "short_answer"),
                confidence = json.optDouble("confidence", 0.0),
                studyTip = json.optString("study_tip", ""),
                relatedConcepts = concepts
            )
        }
    }
}
