package com.example.answerlens

import android.content.Context
import android.net.Uri
import com.example.answerlens.models.ParsedQuestion

object StudyComSearch {
    fun buildSearchUri(context: Context, parsedQuestion: ParsedQuestion): Uri {
        return Uri.parse("https://www.google.com/search?q=${Uri.encode(buildQuery(context, parsedQuestion))}")
    }

    fun buildQuery(context: Context, parsedQuestion: ParsedQuestion): String {
        val courseCode = clean(Prefs.studyCourseCode(context))
        val question = clean(parsedQuestion.question)
        val topic = clean(parsedQuestion.topic)
        val choiceTerms = parsedQuestion.choices
            .map { cleanChoice(it) }
            .filter { it.length >= 4 }
            .take(3)

        val parts = mutableListOf<String>()
        parts.add("site:study.com/academy/lesson")
        if (courseCode.isNotBlank()) parts.add(quote(courseCode))
        if (question.isNotBlank()) parts.add(quote(question.take(140)))
        if (topic.isNotBlank() && !topic.equals("General study", ignoreCase = true)) parts.add(topic)
        choiceTerms.forEach { choice ->
            if (choice.length <= 70) parts.add(quote(choice)) else parts.add(choice.take(70))
        }

        return parts.joinToString(" ").replace(Regex("\\s+"), " ").trim().take(500)
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
