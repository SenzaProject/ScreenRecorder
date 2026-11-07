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
import android.provider.Settings
import android.view.WindowManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "pause"
        private const val CHANNEL = "rec"
        private const val CHANNEL_RESULT = "rec_result"
        private const val NOTIF_ID = 1
        private const val NOTIF_RESULT_ID = 2
        private const val TAG = "RecorderService"

        @Volatile var isRecording = false
        /** (sedangMerekam, pesan) */
        var listener: ((Boolean, String?) -> Unit)? = null
    }

    private var projection: MediaProjection? = null
    private var encoder: ScreenEncoder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var island: IslandOverlay? = null
    private val main = Handler(Looper.getMainLooper())
    /** true selama file sedang difinalisasi di thread latar (main thread tidak boleh diblokir). */
    private var finishing = false

    private val projectionCb = object : MediaProjection.Callback() {
        override fun onStop() { finish(null) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> finish(null)
            ACTION_PAUSE -> togglePause()
        }
        return START_NOT_STICKY
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$what gagal", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun startRecording(i: Intent) {
        // Sudah merekam / sedang menyimpan: abaikan permintaan kedua (token proyeksi baru tidak dipakai)
        if (finishing || projection != null || encoder != null) {
            notifyState("Rekaman sebelumnya masih berjalan atau sedang disimpan")
            return
        }
        val w = i.getIntExtra("w", 1280)
        val h = i.getIntExtra("h", 720)
        val fps = i.getIntExtra("fps", 30)
        val dpi = i.getIntExtra("dpi", 320)
        val dev = i.getBooleanExtra("dev", false)
        val mic = i.getBooleanExtra("mic", false)
        val isl = i.getBooleanExtra("island", false)
        val islCtl = i.getBooleanExtra("islandCtl", true)
        val code = i.getIntExtra("code", 0)
        val data = i.getParcelableExtra<Intent>("data")

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Perekaman layar", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Hasil rekaman", NotificationManager.IMPORTANCE_DEFAULT))
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (mic && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            startForeground(NOTIF_ID, buildNotification(), type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground gagal", e)
            finish("Layanan perekaman tidak bisa dimulai: ${e.message}")
            return
        }

        try {
            if (data == null) throw IllegalStateException("izin perekaman layar tidak diterima")
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data)
            projection = p
            // Wajib didaftarkan sebelum createVirtualDisplay (Android 14+)
            p.registerCallback(projectionCb, main)

            val name = "Screen_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val newUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("tidak bisa membuat file di penyimpanan")
            uri = newUri
            val descriptor = contentResolver.openFileDescriptor(newUri, "rw")
                ?: throw IllegalStateException("tidak bisa membuka file untuk ditulis")
            pfd = descriptor

            val bitrate = Caps.bitrateFor(w, h, fps)
            val e = ScreenEncoder(w, h, fps, bitrate, descriptor.fileDescriptor, p, dev, mic)
            encoder = e
            e.onFatal = { msg -> main.post { finish("Rekaman berhenti: $msg") } }
            e.prepare()
            e.start()
            display = p.createVirtualDisplay(
                "ScreenRecorder", e.width, e.height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                e.surface, null, null
            )
            isRecording = true
            var islandNote = ""
            if (isl) {
                try {
                    val toggle: () -> Boolean = { togglePause() }
                    val acc = IslandAccessibilityService.instance
                    island = when {
                        // Lewat layanan aksesibilitas: window berada di ATAS status bar sehingga pill bisa disentuh
                        acc != null -> IslandOverlay(acc, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, islCtl, fps, toggle) { finish(null) }
                        Settings.canDrawOverlays(this) -> IslandOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, islCtl, fps, toggle) { finish(null) }
                        else -> null
                    }
                    island?.show()
                } catch (ex: Exception) {
                    // Pill hanya pelengkap: kegagalannya tidak boleh membatalkan rekaman
                    Log.w(TAG, "Pill gagal ditampilkan", ex)
                    safely("sembunyikan pill") { island?.hide() }
                    island = null
                    islandNote = " (pill tidak bisa ditampilkan)"
                }
            }
            val audio = when {
                dev && mic -> "suara perangkat + mikrofon"
                dev -> "suara perangkat"
                mic -> "mikrofon"
                else -> "tanpa suara"
            }
            notifyState("Merekam ${e.width}x${e.height} @ ${fps}fps ($audio)$islandNote")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai rekaman", e)
            finish("Gagal merekam: ${e.message}")
        }
    }

    /** Jeda/lanjut dari pill atau notifikasi. @return true jika sekarang dijeda. */
    private fun togglePause(): Boolean {
        val enc = encoder ?: return false
        if (finishing) return enc.isPaused
        val np = !enc.isPaused
        enc.setPaused(np)
        island?.syncPaused(np)
        safely("perbarui notifikasi") {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(np))
        }
        return np
    }

    /**
     * Menghentikan rekaman. Bagian cepat berjalan di main thread; finalisasi file (join thread encoder,
     * muxer.stop) di thread latar supaya tidak memicu ANR.
     */
    private fun finish(error: String?) {
        if (finishing) return
        if (projection == null && encoder == null) {
            // Gagal sebelum ada sesi (mis. startForeground ditolak): tetap beri tahu pengguna
            if (error != null) {
                notifyResult(error, null)
                notifyState(error)
            }
            safely("stopForeground") { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }
        finishing = true
        safely("sembunyikan pill") { island?.hide() }
        island = null
        val wasRecording = isRecording
        isRecording = false
        notifyState(null)
        val e = encoder
        encoder = null
        safely("lepas VirtualDisplay") { display?.release() }
        display = null
        val p = projection
        projection = null
        safely("unregister callback") { p?.unregisterCallback(projectionCb) }
        val u = uri
        uri = null
        val f = pfd
        pfd = null

        Thread({
            var failed = error
            var note: String? = null
            if (e != null) {
                val stopErr: String? = try {
                    e.stop()
                } catch (t: Throwable) {
                    Log.e(TAG, "encoder.stop gagal", t)
                    "Gagal menyelesaikan file: ${t.message}"
                }
                if (failed == null && stopErr != null) failed = stopErr
                if (failed == null && wasRecording && e.videoSamples == 0L) failed = "Tidak ada frame yang terekam"
                if (failed == null && e.error != null) note = "Tersimpan, tapi ada masalah: ${e.error}"
            }
            safely("hentikan proyeksi") { p?.stop() }
            safely("tutup file") { f?.close() }
            if (u != null) {
                safely("finalisasi MediaStore") {
                    if (failed != null) {
                        contentResolver.delete(u, null, null)
                    } else {
                        val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                        contentResolver.update(u, v, null, null)
                    }
                }
            }
            val ok = failed == null
            val msg = failed ?: note ?: "Tersimpan di Movies/ScreenRecorder"
            main.post { completeFinish(msg, if (ok) u else null) }
        }, "rec-finish").start()
    }

    private fun completeFinish(msg: String, savedUri: Uri?) {
        finishing = false
        safely("stopForeground") { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
        notifyResult(msg, savedUri)
        notifyState(msg)
    }

    private fun notifyState(msg: String?) {
        main.post { listener?.invoke(isRecording, msg) }
    }

    /** Hasil rekaman juga lewat notifikasi, karena aplikasi biasanya di background saat rekaman berakhir. */
    private fun notifyResult(msg: String, saved: Uri?) {
        safely("notifikasi hasil") {
            val b = Notification.Builder(this, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(if (saved != null) "Rekaman tersimpan" else "Screen Recorder")
                .setContentText(msg)
                .setAutoCancel(true)
            if (saved != null) {
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(saved, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                b.setContentIntent(
                    PendingIntent.getActivity(this, 1, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            getSystemService(NotificationManager::class.java).notify(NOTIF_RESULT_ID, b.build())
        }
    }

    private fun buildNotification(paused: Boolean = false): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pause = PendingIntent.getService(
            this, 2,
            Intent(this, RecorderService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Screen Recorder")
            .setContentText(if (paused) "Rekaman dijeda" else "Sedang merekam layar…")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icon, if (paused) "Lanjut" else "Jeda", pause).build())
            .addAction(Notification.Action.Builder(icon, "Stop", stop).build())
            .build()
    }

    override fun onDestroy() {
        if (!finishing && (projection != null || encoder != null)) finish(null)
        super.onDestroy()
    }
}
