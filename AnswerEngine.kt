package com.example.answerlens

import android.content.Context
import com.example.answerlens.models.AnswerMode
import com.example.answerlens.models.AnswerResult
import com.example.answerlens.models.ParsedQuestion

class AnswerEngine(private val context: Context) {
    fun answer(question: ParsedQuestion, callback: (AnswerResult, String?) -> Unit) {
        val mode = Prefs.answerMode(context)
        val endpoint = Prefs.apiEndpoint(context)
        if (endpoint.isNotBlank()) {
            SearchRepository(context).requestAnswer(question, mode) { remoteResult, error ->
                if (remoteResult != null) {
                    callback(remoteResult, null)
                } else {
                    val fallback = LocalReasoner.answer(question, mode)
                    callback(fallback, error ?: "Remote answer failed. Showing local fallback.")
                }
            }
        } else {
            callback(LocalReasoner.answer(question, mode), null)
        }
    }
}

private object LocalReasoner {
    private data class Rule(
        val topicContains: String,
        val questionContains: List<String>,
        val answerContains: String,
        val explanation: String,
        val tip: String,
        val concepts: List<String>
    )

    private val rules = listOf(
        Rule(
            topicContains = "java",
            questionContains = listOf("define", "class", "keyword"),
            answerContains = "class",
            explanation = "In Java, the class keyword declares a class, which defines the structure and behavior objects can have.",
            tip = "When Java asks for a blueprint for objects, think class first, then object as the created instance.",
            concepts = listOf("classes", "objects", "constructors", "methods")
        ),
        Rule(
            topicContains = "java",
            questionContains = listOf("entry", "main", "method"),
            answerContains = "main",
            explanation = "Java applications commonly start execution from the main method signature.",
            tip = "For beginner Java questions, main usually points to program entry rather than object design.",
            concepts = listOf("main method", "JVM", "static methods")
        ),
        Rule(
            topicContains = "sql",
            questionContains = listOf("primary key"),
            answerContains = "unique",
            explanation = "A primary key uniquely identifies each row in a table and cannot be null.",
            tip = "Primary key equals unique row identity. Foreign key equals relationship to another table.",
            concepts = listOf("primary keys", "foreign keys", "tables")
        ),
        Rule(
            topicContains = "web",
            questionContains = listOf("style", "css"),
            answerContains = "css",
            explanation = "CSS controls presentation, such as colors, spacing, fonts, and layout.",
            tip = "HTML is structure, CSS is styling, JavaScript is behavior.",
            concepts = listOf("HTML", "CSS", "JavaScript")
        )
    )

    fun answer(parsed: ParsedQuestion, mode: AnswerMode): AnswerResult {
        val lowerQuestion = parsed.question.lowercase()
        val lowerTopic = parsed.topic.lowercase()
        val rule = rules.firstOrNull { rule ->
            lowerTopic.contains(rule.topicContains) && rule.questionContains.all { lowerQuestion.contains(it) }
        }

        val choiceAnswer = if (rule != null && parsed.choices.isNotEmpty()) {
            parsed.choices.firstOrNull { it.lowercase().contains(rule.answerContains) }
        } else null

        val allOfTheseAnswer = chooseAllOfTheseWhenLikely(parsed)
        val guessedChoice = choiceAnswer ?: allOfTheseAnswer ?: chooseByKeywordOverlap(parsed)
        val baseAnswer = guessedChoice ?: directAnswer(parsed, rule)
        val confidence = when {
            choiceAnswer != null -> 0.96
            allOfTheseAnswer != null -> 0.82
            guessedChoice != null -> 0.68
            rule != null -> 0.82
            else -> 0.42
        }

        return when (mode) {
            AnswerMode.EXPLAIN -> AnswerResult(
                likelyAnswer = "Hint first",
                explanation = buildExplainModeExplanation(parsed, rule, guessedChoice),
                confidence = confidence.coerceAtMost(0.80),
                studyTip = rule?.tip ?: "Underline the key verb in the question, then eliminate choices that do not match that verb.",
                relatedConcepts = rule?.concepts ?: relatedConcepts(parsed)
            )
            AnswerMode.RESEARCH -> AnswerResult(
                likelyAnswer = baseAnswer,
                explanation = rule?.explanation ?: explanationFor(parsed, allOfTheseAnswer),
                confidence = confidence,
                studyTip = rule?.tip ?: "Research mode is strongest when connected to your own backend or study-note search index.",
                relatedConcepts = rule?.concepts ?: relatedConcepts(parsed)
            )
            AnswerMode.ANSWER -> AnswerResult(
                likelyAnswer = baseAnswer,
                explanation = rule?.explanation ?: explanationFor(parsed, allOfTheseAnswer),
                confidence = confidence,
                studyTip = rule?.tip ?: "Compare each answer choice to the exact wording of the question instead of relying on familiar-looking terms.",
                relatedConcepts = rule?.concepts ?: relatedConcepts(parsed)
            )
        }
    }

    private fun chooseAllOfTheseWhenLikely(parsed: ParsedQuestion): String? {
        if (parsed.choices.size < 3) return null
        val allChoice = parsed.choices.firstOrNull { choice ->
            val lower = stripLabel(choice).lowercase()
            lower.contains("all of these") || lower.contains("all of the above") || lower.contains("all answers")
        } ?: return null

        val q = parsed.question.lowercase()
        val choicesText = parsed.choices.joinToString(" ") { stripLabel(it).lowercase() }
        val programmingToolboxQuestion =
            q.contains("programmer") && q.contains("toolbox") &&
                    choicesText.contains("math") &&
                    choicesText.contains("logic") &&
                    choicesText.contains("programming instructions") &&
                    choicesText.contains("algorithm")

        if (programmingToolboxQuestion) return allChoice

        val nonAllChoices = parsed.choices.filter { it != allChoice }
        val broadPositiveSignals = listOf("correct", "contains", "include", "includes", "consist", "toolbox", "used for")
        val questionSoundsBroad = broadPositiveSignals.any { q.contains(it) } || q.contains("____")
        val allChoicesLookPlausible = nonAllChoices.count { stripLabel(it).length >= 4 } >= 3
        return if (questionSoundsBroad && allChoicesLookPlausible) allChoice else null
    }

    private fun explanationFor(parsed: ParsedQuestion, allOfTheseAnswer: String?): String {
        if (allOfTheseAnswer != null) {
            return "The question is broad and the other listed choices all fit the concept, so the all-of-these option is the best match."
        }
        return "The likely answer was selected by local keyword and pattern matching. Add an answer API endpoint in Settings for stronger reasoning."
    }

    private fun buildExplainModeExplanation(parsed: ParsedQuestion, rule: Rule?, guessedChoice: String?): String {
        if (rule != null) {
            return "Topic: ${parsed.topic}\n\nHint: Look for the concept connected to ${rule.questionContains.joinToString(" + ")}.\n\nExplanation: ${rule.explanation}"
        }
        val hint = guessedChoice?.let { "One choice shares the most meaning with the question: ${stripLabel(it).take(40)}." }
            ?: "Look for the choice that directly answers the action word in the question."
        return "Topic: ${parsed.topic}\n\nHint: $hint\n\nExplanation: Break the question into its key verb, subject, and requested detail before choosing."
    }

    private fun directAnswer(parsed: ParsedQuestion, rule: Rule?): String {
        if (rule != null) return rule.answerContains
        return when {
            parsed.type == "true_false" -> "Review the statement against the concept before choosing True or False."
            parsed.type == "fill_in_blank" -> "Fill the blank with the term that completes the definition."
            parsed.choices.isNotEmpty() -> parsed.choices.first()
            else -> "No direct answer detected. Add an answer API endpoint for stronger reasoning."
        }
    }

    private fun chooseByKeywordOverlap(parsed: ParsedQuestion): String? {
        if (parsed.choices.isEmpty()) return null
        val questionWords = parsed.question.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .toSet()
        val scored = parsed.choices.map { choice ->
            val words = stripLabel(choice).lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 3 }
                .toSet()
            choice to words.count { it in questionWords }
        }
        val best = scored.maxByOrNull { it.second }
        return if (best != null && best.second > 0) best.first else null
    }

    private fun stripLabel(choice: String): String = choice.replace(Regex("^[A-H0-9]+\\.\\s*"), "")

    private fun relatedConcepts(parsed: ParsedQuestion): List<String> = when {
        parsed.topic.contains("Java", true) -> listOf("syntax", "classes", "methods")
        parsed.topic.contains("SQL", true) -> listOf("tables", "queries", "keys")
        parsed.topic.contains("Web", true) -> listOf("HTML", "CSS", "JavaScript")
        parsed.topic.contains("Networking", true) -> listOf("protocols", "IP", "DNS")
        else -> listOf("key terms", "definitions", "elimination strategy")
    }
}
