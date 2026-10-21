package com.senzaproject.screenrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "rec"

        @Volatile var isRecording = false
        /** (sedangMerekam, pesan) */
        var listener: ((Boolean, String?) -> Unit)? = null
    }

    private var projection: MediaProjection? = null
    private var encoder: ScreenEncoder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private val main = Handler(Looper.getMainLooper())

    private val projectionCb = object : MediaProjection.Callback() {
        override fun onStop() { finish(null) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> finish(null)
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startRecording(i: Intent) {
        val w = i.getIntExtra("w", 1280)
        val h = i.getIntExtra("h", 720)
        val fps = i.getIntExtra("fps", 30)
        val dpi = i.getIntExtra("dpi", 320)
        val dev = i.getBooleanExtra("dev", false)
        val mic = i.getBooleanExtra("mic", false)
        val code = i.getIntExtra("code", 0)
        val data = i.getParcelableExtra<Intent>("data")

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Perekaman layar", NotificationManager.IMPORTANCE_LOW)
        )
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (mic && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(1, buildNotification(), type)

        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data!!)
            projection = p
            p.registerCallback(projectionCb, main)

            val name = "Screen_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            pfd = contentResolver.openFileDescriptor(uri!!, "rw")

            val bitrate = (w.toLong() * h * fps / 10).coerceIn(2_000_000L, 100_000_000L).toInt()
            val e = ScreenEncoder(w, h, fps, bitrate, pfd!!.fileDescriptor, p, dev, mic)
            encoder = e
            e.prepare()
            e.start()
            display = p.createVirtualDisplay(
                "ScreenRecorder", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                e.surface, null, null
            )
            isRecording = true
            val audio = when {
                dev && mic -> "suara perangkat + mikrofon"
                dev -> "suara perangkat"
                mic -> "mikrofon"
                else -> "tanpa suara"
            }
            notifyState("Merekam ${w}x$h @ ${fps}fps ($audio)")
        } catch (e: Exception) {
            finish("Gagal merekam: ${e.message}")
        }
    }

    private fun finish(error: String?) {
        if (projection == null && encoder == null) { stopSelf(); return }
        val wasRecording = isRecording
        isRecording = false
        var failed = error
        var note: String? = null
        val e = encoder
        encoder = null
        if (e != null) {
            runCatching { e.stop() }
            if (failed == null && wasRecording && !e.wrote) failed = "Tidak ada frame yang terekam"
            if (failed == null && e.error != null) note = "Tersimpan, tapi audio bermasalah: ${e.error}"
        }
        runCatching { display?.release() }
        display = null
        val p = projection
        projection = null
        runCatching { p?.unregisterCallback(projectionCb) }
        runCatching { p?.stop() }
        runCatching { pfd?.close() }
        pfd = null
        uri?.let {
            if (failed != null) {
                runCatching { contentResolver.delete(it, null, null) }
            } else {
                val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(it, v, null, null) }
            }
        }
        uri = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        notifyState(failed ?: note ?: "Tersimpan di Movies/ScreenRecorder")
    }

    private fun notifyState(msg: String?) {
        main.post { listener?.invoke(isRecording, msg) }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Screen Recorder")
            .setContentText("Sedang merekam layar…")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icon, "Stop", stop).build())
            .build()
    }

    override fun onDestroy() {
        if (projection != null || encoder != null) finish(null)
        super.onDestroy()
    }
}
