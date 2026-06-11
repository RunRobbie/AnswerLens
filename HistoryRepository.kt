package com.example.answerlens

import android.content.Context
import com.example.answerlens.models.AnswerResult
import com.example.answerlens.models.HistoryItem
import com.example.answerlens.models.ParsedQuestion
import org.json.JSONArray

object HistoryRepository {
    private const val PREF_FILE = "answerlens_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 100

    fun save(context: Context, question: ParsedQuestion, result: AnswerResult) {
        val newItem = HistoryItem(
            question = question.question,
            answer = result.likelyAnswer,
            explanation = result.explanation,
            timestampMillis = System.currentTimeMillis(),
            topic = question.topic,
            type = question.type,
            confidence = result.confidence,
            studyTip = result.studyTip,
            relatedConcepts = result.relatedConcepts
        )

        val existing = load(context)
        val merged = listOf(newItem) + existing
        val arr = JSONArray()
        merged.take(MAX_ITEMS).forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }

    fun load(context: Context): List<HistoryItem> {
        val raw = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val items = mutableListOf<HistoryItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            items.add(HistoryItem.fromJson(obj))
        }
        return items
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ITEMS)
            .apply()
    }
}
