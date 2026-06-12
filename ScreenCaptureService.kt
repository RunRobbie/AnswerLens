package com.example.answerlens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            tearDownProjection(stopProjection = false)
            current = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> startProjection(intent)
        }
        return START_STICKY
    }

    private fun startProjection(intent: Intent) {
        startForegroundNotification()

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)
        createVirtualDisplay()
    }

    private fun createVirtualDisplay() {
        val metrics = resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi

        imageReader?.close()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay?.release()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AnswerLensCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler
        )
    }

    fun captureOnce(callback: (Bitmap?, String?) -> Unit) {
        fun tryAcquire(attempt: Int) {
            mainHandler.postDelayed({
                val reader = imageReader
                if (reader == null) {
                    callback(null, "Screen capture is not ready. Restart the overlay and grant capture permission again.")
                    return@postDelayed
                }

                val image = reader.acquireLatestImage()
                if (image == null) {
                    if (attempt < 5) {
                        tryAcquire(attempt + 1)
                    } else {
                        callback(null, "No screen image was available. Tap Analyze again, or restart AnswerLens and grant screen capture again.")
                    }
                    return@postDelayed
                }

                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val paddedWidth = width + rowPadding / pixelStride

                    val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                    paddedBitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                    if (paddedBitmap != cropped) paddedBitmap.recycle()
                    callback(cropped, null)
                } catch (e: Exception) {
                    callback(null, e.message ?: "Unable to capture the screen.")
                } finally {
                    image.close()
                }
            }, if (attempt == 1) 650L else 250L)
        }

        tryAcquire(1)
    }

    private fun startForegroundNotification() {
        val channelId = "answerlens_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AnswerLens screen capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("AnswerLens is ready")
            .setContentText("Tap the floating Analyze bubble to read the current screen.")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun tearDownProjection(stopProjection: Boolean = true) {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val projection = mediaProjection
        mediaProjection = null
        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        if (stopProjection) {
            try { projection?.stop() } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        tearDownProjection(stopProjection = true)
        current = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.answerlens.START_CAPTURE"
        const val ACTION_STOP = "com.example.answerlens.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val NOTIFICATION_ID = 4107

        @Volatile
        private var current: ScreenCaptureService? = null

        fun captureCurrentScreen(callback: (Bitmap?, String?) -> Unit) {
            val service = current
            if (service == null) {
                callback(null, "Screen capture service is not running. Open AnswerLens and press Start overlay.")
            } else {
                service.captureOnce(callback)
            }
        }
    }
}
