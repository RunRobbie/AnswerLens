package com.example.answerlens

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.answerlens.models.AnswerMode
import com.example.answerlens.models.AnswerResult
import com.example.answerlens.models.ParsedQuestion
import java.text.NumberFormat

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bubble: Button? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var lastParsedQuestion: ParsedQuestion? = null
    private var lastAnswerResult: AnswerResult? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (canDrawOverlays()) createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null && Prefs.bubbleEnabled(this)) createBubble()
        return START_STICKY
    }

    private fun createBubble() {
        if (!Prefs.bubbleEnabled(this) || bubble != null) return
        bubble = Button(this).apply {
            text = "Analyze"
            setAllCaps(false)
            textSize = 14f
            setTextColor(Color.WHITE)
            background = roundRect("#2563EB", dp(18))
        }

        bubbleParams = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = dp(18),
            y = dp(160)
        )

        bubble?.setOnTouchListener(DragTouchListener(windowManager, bubbleParams!!) {
            analyze()
        })

        windowManager.addView(bubble, bubbleParams)
    }

    private fun analyze() {
        setBubbleText("Reading…")
        removePanel()

        ScreenCaptureService.captureCurrentScreen { bitmap, captureError ->
            if (bitmap == null) {
                mainHandler.post { showError(captureError ?: "Capture failed.") }
                return@captureCurrentScreen
            }

            OcrProcessor().recognize(bitmap) { rawText, ocrError ->
                bitmap.recycle()
                if (ocrError != null) {
                    mainHandler.post { showError(ocrError) }
                    return@recognize
                }

                val parsed = QuestionParser.parse(rawText)
                lastParsedQuestion = parsed
                AnswerEngine(applicationContext).answer(parsed) { answerResult, answerError ->
                    mainHandler.post {
                        lastAnswerResult = answerResult
                        setBubbleText("Analyze")
                        showResultPanel(parsed, answerResult, answerError)
                        if (Prefs.saveHistory(this) && Prefs.answerMode(this) != AnswerMode.EXPLAIN) {
                            HistoryRepository.save(this, parsed, answerResult)
                        }
                    }
                }
            }
        }
    }

    private fun showResultPanel(parsed: ParsedQuestion, result: AnswerResult, warning: String?) {
        removePanel()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = roundRect("#111827", dp(18), strokeColor = "#334155")
        }

        val title = TextView(this).apply {
            text = "AnswerLens"
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        content.addView(title, matchWrap())

        if (!warning.isNullOrBlank()) {
            content.addView(label("Note:", warning, "#FBBF24"))
        }

        val mode = Prefs.answerMode(this)
        if (mode == AnswerMode.EXPLAIN) {
            content.addView(label("Topic:", parsed.topic))
            content.addView(label("Detected Question:", parsed.question))
            if (Prefs.showExplanation(this)) content.addView(label("Hint / Explanation:", result.explanation))
        } else {
            content.addView(label("Detected Question:", parsed.question))
            if (Prefs.showAnswer(this)) content.addView(label("Likely Answer:", result.likelyAnswer))
            if (Prefs.showExplanation(this)) content.addView(label("Explanation:", result.explanation))
        }

        if (Prefs.showConfidence(this)) {
            content.addView(label("Confidence:", percent(result.confidence)))
        }
        content.addView(label("Study Tip:", result.studyTip))
        if (result.relatedConcepts.isNotEmpty()) {
            content.addView(label("Related Concepts:", result.relatedConcepts.joinToString(", ")))
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        buttonRow.addView(panelButton("Analyze again") { analyze() })
        buttonRow.addView(panelButton("Save to history") {
            HistoryRepository.save(this, parsed, result)
            Toast.makeText(this, "Saved to history.", Toast.LENGTH_SHORT).show()
        })
        buttonRow.addView(panelButton("Minimize") { removePanel() })
        buttonRow.addView(panelButton("Close") { stopSelf() })
        content.addView(buttonRow, matchWrap())

        val scroll = ScrollView(this).apply {
            addView(content)
        }

        val params = overlayParams(
            width = dp(350),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = dp(14),
            y = dp(80)
        )

        panel = scroll
        windowManager.addView(scroll, params)
    }

    private fun showError(message: String) {
        setBubbleText("Analyze")
        removePanel()
        val parsed = ParsedQuestion(
            question = "No question detected",
            choices = emptyList(),
            type = "short_answer",
            topic = "General study",
            rawText = ""
        )
        val result = AnswerResult(
            likelyAnswer = "Unavailable",
            explanation = message,
            confidence = 0.0,
            studyTip = "Try opening the study app first, then press Analyze again after the screen is stable.",
            relatedConcepts = emptyList()
        )
        showResultPanel(parsed, result, null)
    }

    private fun label(heading: String, body: String, headingColor: String = "#7DD3FC"): TextView = TextView(this).apply {
        text = "$heading\n$body"
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(0, dp(8), 0, dp(4))
        setLineSpacing(2f, 1.0f)
    }

    private fun panelButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setAllCaps(false)
        setOnClickListener { onClick() }
    }

    private fun setBubbleText(text: String) {
        mainHandler.post { bubble?.text = text }
    }

    private fun removePanel() {
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panel = null
    }

    private fun overlayParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun roundRect(color: String, cornerDp: Int, strokeColor: String? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = cornerDp.toFloat()
            if (strokeColor != null) setStroke(dp(1), Color.parseColor(strokeColor))
        }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun percent(value: Double): String = NumberFormat.getPercentInstance().format(value.coerceIn(0.0, 1.0))
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onDestroy() {
        removePanel()
        bubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class DragTouchListener(
        private val wm: WindowManager,
        private val params: WindowManager.LayoutParams,
        private val onClick: () -> Unit
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    wm.updateViewLayout(view, params)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick()
                    return true
                }
            }
            return false
        }
    }
}
