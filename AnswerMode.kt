package com.example.answerlens.models

enum class AnswerMode(val prefValue: String, val displayName: String) {
    EXPLAIN("explain", "Explain Mode"),
    ANSWER("answer", "Answer Mode"),
    RESEARCH("research", "Research Mode");

    companion object {
        fun fromPref(value: String?): AnswerMode = values().firstOrNull { it.prefValue == value } ?: ANSWER
    }
}
