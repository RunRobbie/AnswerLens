package com.example.answerlens

import android.content.Context
import android.graphics.RectF
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
    const val KEY_REGION_ENABLED = "analysis_region_enabled"
    private const val KEY_REGION_LEFT = "analysis_region_left"
    private const val KEY_REGION_TOP = "analysis_region_top"
    private const val KEY_REGION_RIGHT = "analysis_region_right"
    private const val KEY_REGION_BOTTOM = "analysis_region_bottom"

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

    fun analysisRegion(context: Context): RectF? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_REGION_ENABLED, false)) return null
        val left = p.getFloat(KEY_REGION_LEFT, 0f)
        val top = p.getFloat(KEY_REGION_TOP, 0f)
        val right = p.getFloat(KEY_REGION_RIGHT, 1f)
        val bottom = p.getFloat(KEY_REGION_BOTTOM, 1f)
        if (right - left < 0.05f || bottom - top < 0.05f) return null
        return RectF(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )
    }

    fun saveAnalysisRegion(context: Context, region: RectF) {
        val left = region.left.coerceIn(0f, 1f)
        val top = region.top.coerceIn(0f, 1f)
        val right = region.right.coerceIn(left + 0.05f, 1f)
        val bottom = region.bottom.coerceIn(top + 0.05f, 1f)
        prefs(context).edit()
            .putBoolean(KEY_REGION_ENABLED, true)
            .putFloat(KEY_REGION_LEFT, left)
            .putFloat(KEY_REGION_TOP, top)
            .putFloat(KEY_REGION_RIGHT, right)
            .putFloat(KEY_REGION_BOTTOM, bottom)
            .apply()
    }

    fun clearAnalysisRegion(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_REGION_ENABLED, false)
            .remove(KEY_REGION_LEFT)
            .remove(KEY_REGION_TOP)
            .remove(KEY_REGION_RIGHT)
            .remove(KEY_REGION_BOTTOM)
            .apply()
    }
}
