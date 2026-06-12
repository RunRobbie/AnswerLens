package com.example.answerlens

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.answerlens.models.AnswerMode

class SettingsActivity : Activity() {
    private lateinit var bubbleEnabled: CheckBox
    private lateinit var showAnswer: CheckBox
    private lateinit var showExplanation: CheckBox
    private lateinit var showConfidence: CheckBox
    private lateinit var saveHistory: CheckBox
    private lateinit var endpoint: EditText
    private lateinit var apiKey: EditText
    private lateinit var studyCourseCode: EditText
    private lateinit var allowedPackages: EditText
    private lateinit var modeGroup: RadioGroup
    private val modeIds = mutableMapOf<Int, AnswerMode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val prefs = Prefs.prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 28f
        })

        root.addView(section("Answer mode"))
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        AnswerMode.values().forEach { mode ->
            val id = android.view.View.generateViewId()
            modeIds[id] = mode
            modeGroup.addView(RadioButton(this).apply {
                this.id = id
                text = mode.displayName
                isChecked = Prefs.answerMode(this@SettingsActivity) == mode
            })
        }
        root.addView(modeGroup)

        root.addView(section("Overlay and result options"))
        bubbleEnabled = check("Enable floating bubble", Prefs.bubbleEnabled(this))
        showAnswer = check("Show answer immediately", Prefs.showAnswer(this))
        showExplanation = check("Show explanation", Prefs.showExplanation(this))
        showConfidence = check("Show confidence score", Prefs.showConfidence(this))
        saveHistory = check("Save history", Prefs.saveHistory(this))
        listOf(bubbleEnabled, showAnswer, showExplanation, showConfidence, saveHistory).forEach { root.addView(it) }

        root.addView(section("Remote answer endpoint"))
        root.addView(TextView(this).apply {
            text = "Optional. For live search or AI answers, point this at your own backend that accepts the JSON payload and returns the required answer JSON. Leaving it blank uses the built-in local study helper."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })
        endpoint = EditText(this).apply {
            hint = "https://your-server.example/answer"
            setText(Prefs.apiEndpoint(this@SettingsActivity))
            setSingleLine(true)
        }
        apiKey = EditText(this).apply {
            hint = "Optional API token for your backend"
            setText(Prefs.apiKey(this@SettingsActivity))
            setSingleLine(true)
        }
        root.addView(endpoint, matchWrap())
        root.addView(apiKey, matchWrap())

        root.addView(section("Google AI Study.com search"))
        root.addView(TextView(this).apply {
            text = "Optional. Enter the Study.com course code or course title to refine the Google AI Study.com Search button, such as CS 112, Computer Science 112, Business 112, or SQL 107. The search opens Google AI Mode with a site:study.com lesson-page query."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })
        studyCourseCode = EditText(this).apply {
            hint = "Example: Computer Science 112"
            setText(Prefs.studyCourseCode(this@SettingsActivity))
            setSingleLine(true)
        }
        root.addView(studyCourseCode, matchWrap())

        root.addView(section("Use scope"))
        root.addView(TextView(this).apply {
            text = "Use AnswerLens only with study/practice apps you create or have permission to analyze. You can list your own package names here for reference."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })
        allowedPackages = EditText(this).apply {
            hint = "com.example.myjavaquiz, com.example.sqlpractice"
            setText(prefs.getString(Prefs.KEY_ALLOWED_PACKAGES, ""))
            minLines = 2
        }
        root.addView(allowedPackages, matchWrap())

        root.addView(Button(this).apply {
            text = "Save settings"
            setAllCaps(false)
            setOnClickListener { save() }
        })

        root.addView(Button(this).apply {
            text = "Clear history"
            setAllCaps(false)
            setOnClickListener {
                HistoryRepository.clear(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, "History cleared.", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Back"
            setAllCaps(false)
            setOnClickListener { finish() }
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun save() {
        val checkedMode = modeIds[modeGroup.checkedRadioButtonId] ?: AnswerMode.ANSWER
        Prefs.prefs(this).edit()
            .putBoolean(Prefs.KEY_BUBBLE_ENABLED, bubbleEnabled.isChecked)
            .putString(Prefs.KEY_ANSWER_MODE, checkedMode.prefValue)
            .putBoolean(Prefs.KEY_SHOW_ANSWER, showAnswer.isChecked)
            .putBoolean(Prefs.KEY_SHOW_EXPLANATION, showExplanation.isChecked)
            .putBoolean(Prefs.KEY_SHOW_CONFIDENCE, showConfidence.isChecked)
            .putBoolean(Prefs.KEY_SAVE_HISTORY, saveHistory.isChecked)
            .putString(Prefs.KEY_API_ENDPOINT, endpoint.text.toString().trim())
            .putString(Prefs.KEY_API_KEY, apiKey.text.toString().trim())
            .putString(Prefs.KEY_STUDY_COURSE_CODE, studyCourseCode.text.toString().trim())
            .putString(Prefs.KEY_ALLOWED_PACKAGES, allowedPackages.text.toString().trim())
            .apply()
        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
    }

    private fun check(text: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        this.text = text
        isChecked = checked
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
