package com.example.answerlens

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.answerlens.models.AnswerMode
import com.example.answerlens.models.AnswerResult
import com.example.answerlens.models.ParsedQuestion
import java.text.NumberFormat
import kotlin.math.abs

class OverlayService : Service() {
    private enum class SelectionMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bubble: Button? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var areaBubble: Button? = null
    private var areaBubbleParams: WindowManager.LayoutParams? = null
    private var closeBubble: Button? = null
    private var closeBubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var selectorOverlay: View? = null
    private var lastParsedQuestion: ParsedQuestion? = null
    private var lastAnswerResult: AnswerResult? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (canDrawOverlays()) createBubbles()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null && Prefs.bubbleEnabled(this)) createBubbles()
        return START_STICKY
    }

    private fun createBubbles() {
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
        bubble?.setOnTouchListener(DragTouchListener(windowManager, bubbleParams!!) { analyze() })
        windowManager.addView(bubble, bubbleParams)

        areaBubble = Button(this).apply {
            text = "Area"
            setAllCaps(false)
            textSize = 14f
            setTextColor(Color.WHITE)
            background = roundRect("#0F766E", dp(18))
        }
        areaBubbleParams = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = dp(116),
            y = dp(160)
        )
        areaBubble?.setOnTouchListener(DragTouchListener(windowManager, areaBubbleParams!!) { showAreaSelector() })
        windowManager.addView(areaBubble, areaBubbleParams)

        closeBubble = Button(this).apply {
            text = "Exit"
            setAllCaps(false)
            textSize = 14f
            setTextColor(Color.WHITE)
            background = roundRect("#DC2626", dp(18))
        }
        closeBubbleParams = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = dp(188),
            y = dp(160)
        )
        closeBubble?.setOnTouchListener(DragTouchListener(windowManager, closeBubbleParams!!) { shutdownAnswerLens() })
        windowManager.addView(closeBubble, closeBubbleParams)
    }

    private fun analyze() {
        setBubbleText("Reading…")
        removePanel()
        removeSelector()
        setBubblesVisible(false)

        ScreenCaptureService.captureCurrentScreen { bitmap, captureError ->
            if (bitmap == null) {
                mainHandler.post {
                    setBubblesVisible(true)
                    showError(captureError ?: "Capture failed.")
                }
                return@captureCurrentScreen
            }

            val ocrBitmap = cropToAnalysisRegion(bitmap)
            if (ocrBitmap !== bitmap) bitmap.recycle()

            OcrProcessor().recognize(ocrBitmap) { rawText, ocrError ->
                ocrBitmap.recycle()
                if (ocrError != null) {
                    mainHandler.post {
                        setBubblesVisible(true)
                        showError(ocrError)
                    }
                    return@recognize
                }

                val parsed = QuestionParser.parse(rawText)
                lastParsedQuestion = parsed
                AnswerEngine(applicationContext).answer(parsed) { answerResult, answerError ->
                    mainHandler.post {
                        lastAnswerResult = answerResult
                        setBubblesVisible(true)
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

    private fun cropToAnalysisRegion(bitmap: Bitmap): Bitmap {
        val region = Prefs.analysisRegion(this) ?: return bitmap
        if (bitmap.width < 20 || bitmap.height < 20) return bitmap

        val left = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 2)
        val top = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 2)
        val right = (region.right * bitmap.width).toInt().coerceIn(left + 2, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(top + 2, bitmap.height)
        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth < 20 || cropHeight < 20) return bitmap

        return try {
            Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
        } catch (_: Exception) {
            bitmap
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
            text = "AnswerLens  •  drag here"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(title, matchWrap())

        if (!warning.isNullOrBlank()) {
            content.addView(label("Note:", warning, "#FBBF24"))
        }

        content.addView(label("Answer:", result.likelyAnswer))

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        buttonRow.addView(panelButton("Analyze again") { analyze() })
        buttonRow.addView(panelButton("Google AI Study.com Search") { openStudyComSearch(parsed) })
        buttonRow.addView(panelButton("Settings / Course code") { openSettings() })
        buttonRow.addView(panelButton("Select analysis area") { showAreaSelector() })
        buttonRow.addView(panelButton("Clear analysis area") {
            Prefs.clearAnalysisRegion(this)
            Toast.makeText(this, "Analysis area cleared. Next analyze will use full screen.", Toast.LENGTH_SHORT).show()
        })
        buttonRow.addView(panelButton("Save to history") {
            HistoryRepository.save(this, parsed, result)
            Toast.makeText(this, "Saved to history.", Toast.LENGTH_SHORT).show()
        })
        buttonRow.addView(panelButton("Minimize") { removePanel() })
        buttonRow.addView(panelButton("Close AnswerLens") { shutdownAnswerLens() })
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
        title.setOnTouchListener(DragTouchListener(windowManager, params, scroll) {})

        panel = scroll
        panelParams = params
        windowManager.addView(scroll, params)
    }

    private fun showAreaSelector() {
        removePanel()
        removeSelector()

        val selectorView = AreaSelectionView(this)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(selectorView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val instructions = TextView(this).apply {
            text = "Drag or resize the box around the question and choices"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundRect("#111827", dp(12), strokeColor = "#334155")
        }
        root.addView(instructions, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply { setMargins(dp(10), dp(16), dp(10), 0) })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(14))
            background = roundRect("#111827", dp(12), strokeColor = "#334155")
        }
        controls.addView(selectionButton("Save area") {
            Prefs.saveAnalysisRegion(this, selectorView.normalizedRegion())
            removeSelector()
            Toast.makeText(this, "Analysis area saved. Tap Analyze.", Toast.LENGTH_SHORT).show()
        })
        controls.addView(selectionButton("Clear") {
            Prefs.clearAnalysisRegion(this)
            removeSelector()
            Toast.makeText(this, "Analysis area cleared.", Toast.LENGTH_SHORT).show()
        })
        controls.addView(selectionButton("Cancel") { removeSelector() })
        root.addView(controls, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { setMargins(dp(10), 0, dp(10), dp(18)) })

        selectorOverlay = root
        windowManager.addView(root, overlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            x = 0,
            y = 0
        ))
    }


    private fun openStudyComSearch(parsed: ParsedQuestion) {
        val intent = Intent(Intent.ACTION_VIEW, StudyComSearch.buildSearchUri(this, parsed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("com.android.browser.application_id", packageName)
            putExtra("create_new_tab", false)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser app found for Study.com search.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Could not open Study.com search.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Could not open settings.", Toast.LENGTH_LONG).show()
        }
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
            likelyAnswer = "Could not read the screen. Tap Clear analysis area, then Analyze again. If it still fails, restart AnswerLens and grant screen capture again.",
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

    private fun selectionButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setAllCaps(false)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        }
    }

    private fun setBubbleText(text: String) {
        mainHandler.post { bubble?.text = text }
    }

    private fun setBubblesVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        bubble?.visibility = visibility
        areaBubble?.visibility = visibility
        closeBubble?.visibility = visibility
    }

    private fun shutdownAnswerLens() {
        removePanel()
        removeSelector()
        Toast.makeText(this, "AnswerLens closed.", Toast.LENGTH_SHORT).show()
        stopService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        stopSelf()
    }

    private fun removePanel() {
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panel = null
        panelParams = null
    }

    private fun removeSelector() {
        selectorOverlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        selectorOverlay = null
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
        removeSelector()
        bubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        areaBubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        closeBubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubble = null
        areaBubble = null
        closeBubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class DragTouchListener(
        private val wm: WindowManager,
        private val params: WindowManager.LayoutParams,
        private val targetView: View? = null,
        private val onClick: () -> Unit
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val target = targetView ?: view
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
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    wm.updateViewLayout(target, params)
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

    private inner class AreaSelectionView(context: Context) : View(context) {
        private val shadePaint = Paint().apply { color = Color.argb(115, 0, 0, 0) }
        private val fillPaint = Paint().apply { color = Color.argb(55, 37, 99, 235) }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(250, 204, 21)
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val rect = RectF()
        private var initialized = false
        private var mode = SelectionMode.NONE
        private var lastX = 0f
        private var lastY = 0f
        private val minSize = dp(80).toFloat()
        private val handleSize = dp(34).toFloat()

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (!initialized && w > 0 && h > 0) {
                val saved = Prefs.analysisRegion(this@OverlayService)
                if (saved != null) {
                    rect.set(saved.left * w, saved.top * h, saved.right * w, saved.bottom * h)
                } else {
                    rect.set(w * 0.05f, h * 0.20f, w * 0.95f, h * 0.72f)
                }
                initialized = true
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)
            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, borderPaint)
            drawHandle(canvas, rect.left, rect.top)
            drawHandle(canvas, rect.right, rect.top)
            drawHandle(canvas, rect.left, rect.bottom)
            drawHandle(canvas, rect.right, rect.bottom)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    mode = hitMode(event.x, event.y)
                    if (mode == SelectionMode.NONE) {
                        rect.set(event.x, event.y, event.x + minSize, event.y + minSize)
                        mode = SelectionMode.BOTTOM_RIGHT
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    updateRect(mode, dx, dy)
                    lastX = event.x
                    lastY = event.y
                    keepInBounds()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mode = SelectionMode.NONE
                    keepInBounds()
                    invalidate()
                    return true
                }
            }
            return true
        }

        fun normalizedRegion(): RectF {
            keepInBounds()
            return RectF(
                (rect.left / width.toFloat()).coerceIn(0f, 1f),
                (rect.top / height.toFloat()).coerceIn(0f, 1f),
                (rect.right / width.toFloat()).coerceIn(0f, 1f),
                (rect.bottom / height.toFloat()).coerceIn(0f, 1f)
            )
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
            canvas.drawCircle(x, y, dp(7).toFloat(), handlePaint)
        }

        private fun hitMode(x: Float, y: Float): SelectionMode {
            fun near(cx: Float, cy: Float) = abs(x - cx) <= handleSize && abs(y - cy) <= handleSize
            return when {
                near(rect.left, rect.top) -> SelectionMode.TOP_LEFT
                near(rect.right, rect.top) -> SelectionMode.TOP_RIGHT
                near(rect.left, rect.bottom) -> SelectionMode.BOTTOM_LEFT
                near(rect.right, rect.bottom) -> SelectionMode.BOTTOM_RIGHT
                rect.contains(x, y) -> SelectionMode.MOVE
                else -> SelectionMode.NONE
            }
        }

        private fun updateRect(mode: SelectionMode, dx: Float, dy: Float) {
            when (mode) {
                SelectionMode.MOVE -> rect.offset(dx, dy)
                SelectionMode.TOP_LEFT -> { rect.left += dx; rect.top += dy }
                SelectionMode.TOP_RIGHT -> { rect.right += dx; rect.top += dy }
                SelectionMode.BOTTOM_LEFT -> { rect.left += dx; rect.bottom += dy }
                SelectionMode.BOTTOM_RIGHT -> { rect.right += dx; rect.bottom += dy }
                SelectionMode.NONE -> Unit
            }
            if (rect.width() < minSize) rect.right = rect.left + minSize
            if (rect.height() < minSize) rect.bottom = rect.top + minSize
        }

        private fun keepInBounds() {
            if (rect.left > rect.right) {
                val oldLeft = rect.left
                rect.left = rect.right
                rect.right = oldLeft
            }
            if (rect.top > rect.bottom) {
                val oldTop = rect.top
                rect.top = rect.bottom
                rect.bottom = oldTop
            }
            if (rect.width() < minSize) rect.right = rect.left + minSize
            if (rect.height() < minSize) rect.bottom = rect.top + minSize
            if (rect.left < 0f) rect.offset(-rect.left, 0f)
            if (rect.top < 0f) rect.offset(0f, -rect.top)
            if (rect.right > width) rect.offset(width - rect.right, 0f)
            if (rect.bottom > height) rect.offset(0f, height - rect.bottom)
        }

    }
}
