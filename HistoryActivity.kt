package com.example.answerlens

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "History"
            textSize = 28f
        })

        val items = HistoryRepository.load(this)
        if (items.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No analyzed questions saved yet."
                textSize = 16f
                setPadding(0, dp(16), 0, dp(16))
            })
        } else {
            items.forEach { item ->
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(item.timestampMillis))
                root.addView(TextView(this).apply {
                    text = buildString {
                        append(date)
                        append("\n")
                        append(item.topic)
                        append(" • ")
                        append(item.type)
                        append("\n\nQuestion:\n")
                        append(item.question)
                        append("\n\nAnswer:\n")
                        append(item.answer)
                        append("\n\nExplanation:\n")
                        append(item.explanation)
                        append("\n\nConfidence: ")
                        append((item.confidence * 100).toInt())
                        append("%")
                        if (item.studyTip.isNotBlank()) {
                            append("\n\nStudy Tip:\n")
                            append(item.studyTip)
                        }
                    }
                    textSize = 14f
                    setPadding(0, dp(18), 0, dp(18))
                })
            }
        }

        root.addView(Button(this).apply {
            text = "Clear history"
            setAllCaps(false)
            setOnClickListener {
                HistoryRepository.clear(this@HistoryActivity)
                render()
            }
        })

        root.addView(Button(this).apply {
            text = "Back"
            setAllCaps(false)
            setOnClickListener { finish() }
        })

        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(scroll)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
