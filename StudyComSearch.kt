package com.example.answerlens

import android.content.Context
import android.net.Uri
import com.example.answerlens.models.ParsedQuestion

object StudyComSearch {
    fun buildSearchUri(context: Context, parsedQuestion: ParsedQuestion): Uri {
        return Uri.Builder()
            .scheme("https")
            .authority("www.google.com")
            .path("search")
            .appendQueryParameter("udm", "50")
            .appendQueryParameter("q", buildQuery(context, parsedQuestion))
            .build()
    }

    fun buildQuery(context: Context, parsedQuestion: ParsedQuestion): String {
        val courseCode = clean(Prefs.studyCourseCode(context))
        val question = clean(parsedQuestion.question)
        val topic = clean(parsedQuestion.topic)
        val choiceTerms = parsedQuestion.choices
            .map { cleanChoice(it) }
            .filter { it.length >= 4 }
            .take(5)

        val parts = mutableListOf<String>()
        parts.add("site:study.com/academy/lesson")
        parts.add("correct answer")
        if (courseCode.isNotBlank()) parts.add(quote(courseCode))
        if (question.isNotBlank()) parts.add(quote(question.take(160)))
        if (topic.isNotBlank() && !topic.equals("General study", ignoreCase = true)) parts.add(topic)
        choiceTerms.forEach { choice ->
            val trimmed = choice.take(95)
            parts.add(quote(trimmed))
        }

        return parts.joinToString(" ").replace(Regex("\\s+"), " ").trim().take(850)
    }

    private fun cleanChoice(value: String): String = clean(
        value.replace(Regex("^[A-Ha-h0-9]+[.)]\\s*"), "")
            .replace(Regex("^[○◯Oo0]\\s+"), "")
    )

    private fun clean(value: String): String = value
        .replace("_", " ")
        .replace(Regex("[^A-Za-z0-9 .,?'/:+&-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun quote(value: String): String = "\"${value.replace("\"", "").trim()}\""
}
