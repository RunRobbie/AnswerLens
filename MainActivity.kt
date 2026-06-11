package com.example.answerlens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val requestProjectionCode = 771
    private val requestNotificationsCode = 772

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "AnswerLens"
            textSize = 30f
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val subtitle = TextView(this).apply {
            text = "Personal study-guide overlay for your own practice apps. Start the overlay, open your study app, then tap Analyze."
            textSize = 16f
            setPadding(0, dp(12), 0, dp(18))
        }

        val permissionText = TextView(this).apply {
            text = buildString {
                append("Overlay permission: ")
                append(if (canDrawOverlays()) "Granted" else "Needed")
                append("\nScreen capture: Requested when you start the overlay")
            }
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        }

        root.addView(title, matchWrap())
        root.addView(subtitle, matchWrap())
        root.addView(permissionText, matchWrap())

        root.addView(primaryButton("Start overlay") { startAnswerLens() })
        root.addView(button("Stop overlay") { stopAnswerLens() })
        root.addView(button("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(button("History") { startActivity(Intent(this, HistoryActivity::class.java)) })

        val scrollView = ScrollView(this)
        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun startAnswerLens() {
        if (!canDrawOverlays()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Grant Draw over other apps, then return to AnswerLens.", Toast.LENGTH_LONG).show()
            return
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestProjectionCode)
    }

    private fun stopAnswerLens() {
        stopService(Intent(this, OverlayService::class.java))
        stopService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        Toast.makeText(this, "AnswerLens stopped.", Toast.LENGTH_SHORT).show()
        render()
    }

    @Deprecated("Deprecated in Android, but still fine for this small no-AndroidX Activity prototype.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestProjectionCode) {
            if (resultCode == RESULT_OK && data != null) {
                val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(captureIntent)
                } else {
                    startService(captureIntent)
                }
                startService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Overlay started. Open your study app and tap Analyze.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Screen capture permission was not granted.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestNotificationsCode)
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setAllCaps(false)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    private fun primaryButton(text: String, onClick: () -> Unit): Button = button(text, onClick).apply {
        textSize = 18f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
