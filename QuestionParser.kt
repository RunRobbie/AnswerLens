package com.example.answerlens

import com.example.answerlens.models.ParsedQuestion

object QuestionParser {
    private val labeledChoice = Regex("^\\s*([A-Ha-h]|[1-9][0-9]?)\\s*[.)]\\s+(.+)$")
    private val trueFalseLine = Regex("^\\s*(true|false)\\s*$", RegexOption.IGNORE_CASE)
    private val radioPrefix = Regex("^\\s*[○◯●◉oO0]\\s+")

    fun parse(rawText: String): ParsedQuestion {
        val cleaned = clean(rawText)
        val lines = cleaned.lines()
            .map { normalizeLine(it) }
            .filter { it.isNotBlank() && !isNoiseLine(it) }
            .fold(mutableListOf<String>()) { acc, line ->
                if (acc.lastOrNull() != line) acc.add(line)
                acc
            }

        val labeledChoices = mutableListOf<String>()
        val nonChoiceLines = mutableListOf<String>()

        for (line in lines) {
            val choiceMatch = labeledChoice.find(line)
            when {
                choiceMatch != null -> {
                    val label = choiceMatch.groupValues[1].uppercase()
                    val value = choiceMatch.groupValues[2].trim()
                    labeledChoices.add("$label. $value")
                }
                trueFalseLine.matches(line) -> labeledChoices.add(line.replaceFirstChar { it.uppercase() })
                else -> nonChoiceLines.add(line)
            }
        }

        val questionIndex = detectQuestionIndex(nonChoiceLines)
        val question = when {
            questionIndex >= 0 -> nonChoiceLines[questionIndex]
            else -> detectQuestion(nonChoiceLines, lines)
        }

        val choices = if (labeledChoices.isNotEmpty()) {
            labeledChoices
        } else {
            detectUnlabeledChoices(nonChoiceLines, questionIndex)
        }

        val type = detectType(question, choices)
        val topic = detectTopic(cleaned)

        return ParsedQuestion(
            question = question.ifBlank { cleaned.take(300) },
            choices = choices,
            type = type,
            topic = topic,
            rawText = cleaned
        )
    }

    fun clean(rawText: String): String {
        val normalized = rawText
            .replace('\u00A0', ' ')
            .replace(Regex("[“”]"), "\"")
            .replace(Regex("[‘’]"), "'")
            .replace(Regex("(?i)\\bciass\\b"), "class")
            .replace(Regex("(?i)\\bpubiic\\b"), "public")
            .replace(Regex("(?i)\\bvoi d\\b"), "void")
            .replace(Regex("(?i)\\bstrinq\\b"), "String")

        val cleanedLines = normalized.lines().map { line ->
            line.trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("^([A-Ha-h]|[1-9][0-9]?)\\s+"), "${'$'}1. ")
                .replace(Regex("^([A-Ha-h])\\s*[,:;-]\\s*"), "${'$'}1. ")
        }

        val deduped = mutableListOf<String>()
        for (line in cleanedLines) {
            if (line.isBlank()) continue
            if (deduped.lastOrNull() != line) deduped.add(line)
        }
        return deduped.joinToString("\n")
    }

    private fun normalizeLine(line: String): String {
        return line.trim()
            .replace(Regex("\\s+"), " ")
            .replace(radioPrefix, "")
            .trim()
    }

    private fun isNoiseLine(line: String): Boolean {
        val lower = line.lowercase().trim()
        return lower.matches(Regex("question\\s*\\d+")) ||
                lower.matches(Regex("lesson\\s*\\d+\\s*quiz")) ||
                lower.matches(Regex("\\d+%\\s*complete")) ||
                lower in setOf("next", "previous", "submit", "close", "analyze", "answerlens") ||
                lower.length <= 1
    }

    private fun detectQuestionIndex(lines: List<String>): Int {
        if (lines.isEmpty()) return -1

        val blankIndex = lines.indexOfFirst { it.contains("____") || it.contains("_____") || it.contains("[blank]", true) }
        if (blankIndex >= 0) return blankIndex

        val questionMarkIndex = lines.indexOfLast { it.endsWith("?") }
        if (questionMarkIndex >= 0) return questionMarkIndex

        val instructionWords = listOf(
            "which", "what", "when", "where", "why", "how", "select", "choose",
            "identify", "define", "fill", "complete", "true or false"
        )
        return lines.indexOfLast { line ->
            val lower = line.lowercase()
            instructionWords.any { lower.contains(it) }
        }
    }

    private fun detectUnlabeledChoices(lines: List<String>, questionIndex: Int): MutableList<String> {
        if (questionIndex < 0 || questionIndex >= lines.lastIndex) return mutableListOf()

        val choices = mutableListOf<String>()
        for (line in lines.drop(questionIndex + 1)) {
            if (isNoiseLine(line)) continue
            if (line.endsWith("?") && choices.isNotEmpty()) break
            if (looksLikeChoice(line)) choices.add(line)
            if (choices.size >= 8) break
        }
        return choices
    }

    private fun looksLikeChoice(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.startsWith("detected question") || lower.startsWith("likely answer")) return false
        if (lower.startsWith("explanation") || lower.startsWith("confidence")) return false
        if (line.length < 2 || line.length > 180) return false
        return true
    }

    private fun detectQuestion(nonChoiceLines: List<String>, allLines: List<String>): String {
        if (nonChoiceLines.isEmpty()) return allLines.firstOrNull().orEmpty()
        val explicit = nonChoiceLines.lastOrNull { it.endsWith("?") }
        if (!explicit.isNullOrBlank()) return explicit

        val instructionWords = listOf(
            "which", "what", "when", "where", "why", "how", "select", "choose",
            "identify", "define", "fill", "complete", "true or false"
        )
        val likely = nonChoiceLines.lastOrNull { line ->
            instructionWords.any { line.lowercase().contains(it) }
        }
        if (!likely.isNullOrBlank()) return likely

        return nonChoiceLines.takeLast(2).joinToString(" ")
    }

    private fun detectType(question: String, choices: List<String>): String {
        val lowerQuestion = question.lowercase()
        val choiceValues = choices.map { it.lowercase().removePrefix("a. ").removePrefix("b. ").trim() }
        return when {
            choices.any { it.matches(Regex("(?i)^true$|^false$")) } ||
                    choiceValues.contains("true") && choiceValues.contains("false") -> "true_false"
            lowerQuestion.contains("____") || lowerQuestion.contains("[blank]") || lowerQuestion.contains("missing word") -> "fill_in_blank"
            choices.isNotEmpty() && choices.all { it.matches(Regex("^[0-9]+\\..+")) } -> "numbered_choice"
            choices.isNotEmpty() -> "multiple_choice"
            else -> "short_answer"
        }
    }

    private fun detectTopic(text: String): String {
        val lower = text.lowercase()
        return when {
            listOf("java", "class", "object", "constructor", "method", "public static void").any { lower.contains(it) } -> "Java programming"
            listOf("sql", "select", "join", "database", "primary key", "foreign key").any { lower.contains(it) } -> "Databases / SQL"
            listOf("python", "def ", "tuple", "dictionary", "list comprehension").any { lower.contains(it) } -> "Python programming"
            listOf("html", "css", "javascript", "dom", "selector").any { lower.contains(it) } -> "Web development"
            listOf("algorithm", "programmer", "programming instructions", "math and logic").any { lower.contains(it) } -> "Programming basics"
            listOf("network", "tcp", "udp", "ip address", "dns", "router", "subnet").any { lower.contains(it) } -> "Networking"
            listOf("agile", "scrum", "sprint", "product owner", "stakeholder").any { lower.contains(it) } -> "Project management / Agile"
            listOf("security", "encryption", "authentication", "authorization", "malware").any { lower.contains(it) } -> "Cybersecurity"
            else -> "General study"
        }
    }
}
