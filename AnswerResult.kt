package com.example.answerlens.models

import org.json.JSONArray
import org.json.JSONObject

data class AnswerResult(
    val likelyAnswer: String,
    val explanation: String,
    val confidence: Double,
    val studyTip: String,
    val relatedConcepts: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("likely_answer", likelyAnswer)
        put("explanation", explanation)
        put("confidence", confidence)
        put("study_tip", studyTip)
        put("related_concepts", JSONArray(relatedConcepts))
    }

    companion object {
        fun fromJson(json: JSONObject): AnswerResult {
            val concepts = mutableListOf<String>()
            val arr = json.optJSONArray("related_concepts") ?: JSONArray()
            for (i in 0 until arr.length()) concepts.add(arr.optString(i))
            val rawConfidence = json.optDouble("confidence", 0.35)
            val normalizedConfidence = if (rawConfidence > 1.0) rawConfidence / 100.0 else rawConfidence
            return AnswerResult(
                likelyAnswer = json.optString("likely_answer", "Unknown"),
                explanation = json.optString("explanation", "No explanation returned."),
                confidence = normalizedConfidence,
                studyTip = json.optString("study_tip", "Review the key terms in the question and compare them to each choice."),
                relatedConcepts = concepts
            )
        }
    }
}
