package com.example.answerlens

import android.content.Context
import com.example.answerlens.models.AnswerMode

object Prefs {
    private const val FILE = "answerlens_prefs"

    const val KEY_BUBBLE_ENABLED = "bubble_enabled"
    const val KEY_ANSWER_MODE = "answer_mode"
    const val KEY_SHOW_ANSWER = "show_answer"
    const val KEY_SHOW_EXPLANATION = "show_explanation"
    const val KEY_SHOW_CONFIDENCE = "show_confidence"
    const val KEY_SAVE_HISTORY = "save_history"
    const val KEY_API_ENDPOINT = "api_endpoint"
    const val KEY_API_KEY = "api_key"
    const val KEY_ALLOWED_PACKAGES = "allowed_packages"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun answerMode(context: Context): AnswerMode =
        AnswerMode.fromPref(prefs(context).getString(KEY_ANSWER_MODE, AnswerMode.ANSWER.prefValue))

    fun bubbleEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BUBBLE_ENABLED, true)
    fun showAnswer(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_ANSWER, true)
    fun showExplanation(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_EXPLANATION, true)
    fun showConfidence(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_CONFIDENCE, true)
    fun saveHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVE_HISTORY, true)
    fun apiEndpoint(context: Context): String = prefs(context).getString(KEY_API_ENDPOINT, "")?.trim().orEmpty()
    fun apiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "")?.trim().orEmpty()
    fun allowedPackages(context: Context): String = prefs(context).getString(KEY_ALLOWED_PACKAGES, "")?.trim().orEmpty()
}
