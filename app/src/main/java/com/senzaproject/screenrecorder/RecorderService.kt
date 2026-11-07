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
import android.view.Display
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
import kotlin.math.min

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

        /** Orientasi layar harus stabil selama ini sebelum rekaman pindah ke file baru (menahan kedipan saat transisi/animasi). */
        private const val ROTATE_SETTLE_MS = 600L

        @Volatile var isRecording = false
        /** (sedangMerekam, pesan) */
        var listener: ((Boolean, String?) -> Unit)? = null
    }

    /**
     * Satu file MP4 = satu segmen. Ukuran video tidak bisa berubah di tengah file, jadi saat layar diputar
     * (mis. beranda portrait -> game landscape) rekaman pindah ke segmen baru yang ukurannya mengikuti
     * orientasi baru. Dengan begitu isi layar selalu memenuhi frame, tidak mengecil di tengah.
     */
    private class Segment(
        val enc: ScreenEncoder,
        val pfd: ParcelFileDescriptor,
        val uri: Uri,
        val landscape: Boolean
    )

    /** Hasil penutupan satu segmen. [uri] null = file dibuang (gagal / kosong). */
    private class Outcome(val uri: Uri?, val error: String?, val note: String?)

    private var projection: MediaProjection? = null
    private var seg: Segment? = null
    private var display: VirtualDisplay? = null
    private var island: IslandOverlay? = null
    private val main = Handler(Looper.getMainLooper())
    /** true selama file sedang difinalisasi di thread latar (main thread tidak boleh diblokir). */
    private var finishing = false

    // Parameter sesi rekaman (tetap selama satu sesi)
    private var recFps = 30
    private var recDpi = 320
    private var recShort = 1080
    private var recDev = false
    private var recMic = false
    private var baseName = ""
    private var segCount = 0

    // Status sesi (hanya disentuh dari main thread)
    private var paused = false
    private var rotating = false
    private var rotationDisabled = false
    private var pendingFinish = false
    private var pendingError: String? = null
    private val outcomes = mutableListOf<Outcome>()

    private val projectionCb = object : MediaProjection.Callback() {
        override fun onStop() { finish(null) }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) scheduleOrientationCheck()
        }
    }

    private val orientationCheck = Runnable { checkOrientation() }

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
        if (finishing || projection != null || seg != null) {
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

        recFps = fps
        recDpi = dpi
        recShort = i.getIntExtra("short", min(w, h))
        recDev = dev
        recMic = mic
        segCount = 0
        paused = false
        rotating = false
        rotationDisabled = false
        pendingFinish = false
        pendingError = null
        outcomes.clear()

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

            baseName = "Screen_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val s = createSegment(p, w, h)
            seg = s
            val e = s.enc
            // Android 14+: satu proyeksi hanya boleh membuat SATU VirtualDisplay. Saat layar diputar,
            // display yang sama di-resize dan surface-nya diganti (lihat rotateSegment).
            display = p.createVirtualDisplay(
                "ScreenRecorder", e.width, e.height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                e.surface, null, null
            )
            isRecording = true
            safely("pantau rotasi layar") {
                getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, main)
            }
            // Layar mungkin sudah berputar di antara permintaan ukuran dan sekarang
            scheduleOrientationCheck()
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

    // ---------- segmen ----------

    /**
     * Membuat file MediaStore + encoder + mulai merekam ke dalamnya. Memblokir (GL, codec, audio);
     * aman dipanggil dari thread latar. Jika gagal, semua yang sudah dibuat dibersihkan lalu exception dilempar.
     */
    private fun createSegment(p: MediaProjection, w: Int, h: Int): Segment {
        segCount++
        val name = baseName + (if (segCount > 1) "_$segCount" else "") + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecorder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val newUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("tidak bisa membuat file di penyimpanan")
        var descriptor: ParcelFileDescriptor? = null
        var enc: ScreenEncoder? = null
        try {
            val pfd = contentResolver.openFileDescriptor(newUri, "rw")
                ?: throw IllegalStateException("tidak bisa membuka file untuk ditulis")
            descriptor = pfd
            val e = ScreenEncoder(
                w, h, recFps, Caps.bitrateFor(w, h, recFps),
                pfd.fileDescriptor, p, recDev, recMic
            )
            enc = e
            e.onFatal = { msg -> main.post { if (!finishing) finish("Rekaman berhenti: $msg") } }
            e.prepare()
            e.start()
            return Segment(e, pfd, newUri, e.width > e.height)
        } catch (t: Throwable) {
            enc?.let { x -> runCatching { x.stop() } }
            runCatching { descriptor?.close() }
            runCatching { contentResolver.delete(newUri, null, null) }
            throw t
        }
    }

    /**
     * Menutup satu segmen (blokir; thread latar saja). [fatal] != null = rekaman gagal, file dibuang.
     * Segmen tanpa satu pun frame juga dibuang. Display HARUS sudah dilepas dari surface segmen ini.
     */
    private fun finalizeSegment(s: Segment, fatal: String?, requireFrames: Boolean): Outcome {
        var failed = fatal
        var note: String? = null
        val stopErr: String? = try {
            s.enc.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "encoder.stop gagal", t)
            "Gagal menyelesaikan file: ${t.message}"
        }
        if (failed == null && stopErr != null) failed = stopErr
        if (failed == null && requireFrames && s.enc.videoSamples == 0L) failed = "Tidak ada frame yang terekam"
        if (failed == null && s.enc.error != null) note = s.enc.error
        safely("tutup file") { s.pfd.close() }
        safely("finalisasi MediaStore") {
            if (failed != null) {
                contentResolver.delete(s.uri, null, null)
            } else {
                val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(s.uri, v, null, null)
            }
        }
        return Outcome(if (failed == null) s.uri else null, failed, note)
    }

    // ---------- rotasi layar ----------

    private fun scheduleOrientationCheck() {
        main.removeCallbacks(orientationCheck)
        main.postDelayed(orientationCheck, ROTATE_SETTLE_MS)
    }

    private fun checkOrientation() {
        val cur = seg ?: return
        if (finishing || rotating || rotationDisabled) return
        if (Caps.isLandscapeNow(this) != cur.landscape) rotateSegment(cur)
    }

    /** Menutup segmen lama dan memulai segmen baru berukuran sesuai orientasi sekarang. Berjalan di thread latar. */
    private fun rotateSegment(old: Segment) {
        val p = projection ?: return
        rotating = true
        // Hentikan aliran frame ke segmen lama sebelum ditutup (display tetap hidup, hanya tanpa surface)
        safely("lepas surface") { display?.setSurface(null) }
        val (w, h) = Caps.dimsNow(this, recShort)
        Thread({
            val outcome = finalizeSegment(old, null, true)
            var next: Segment? = null
            var fellBack = false
            var err: String? = null
            try {
                next = createSegment(p, w, h)
            } catch (t: Throwable) {
                Log.e(TAG, "Segmen baru ${w}x$h gagal dibuat, memakai ukuran lama", t)
                try {
                    // Cadangan: lanjut dengan ukuran sebelumnya (isi layar bisa mengecil, tapi rekaman tidak putus)
                    next = createSegment(p, old.enc.width, old.enc.height)
                    fellBack = true
                } catch (t2: Throwable) {
                    Log.e(TAG, "Segmen cadangan juga gagal", t2)
                    err = t2.message ?: t.message ?: "tidak diketahui"
                }
            }
            val made = next
            val fb = fellBack
            val e = err
            main.post { onRotated(outcome, made, fb, e) }
        }, "rec-rotate").start()
    }

    private fun onRotated(outcome: Outcome, next: Segment?, fellBack: Boolean, err: String?) {
        outcomes += outcome
        rotating = false
        if (next == null) {
            seg = null
            val e = pendingError
            pendingFinish = false
            pendingError = null
            finish(e ?: "tidak bisa melanjutkan rekaman setelah layar diputar ($err)")
            return
        }
        seg = next
        if (fellBack) rotationDisabled = true
        if (pendingFinish) {
            // Stop diminta saat pergantian segmen berlangsung: tutup segmen baru ini juga
            val e = pendingError
            pendingFinish = false
            pendingError = null
            finish(e)
            return
        }
        if (paused) next.enc.setPaused(true)
        try {
            val d = display
            d?.resize(next.enc.width, next.enc.height, recDpi)
            d?.setSurface(next.enc.surface)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal memasang segmen baru ke layar", t)
            finish("Gagal melanjutkan rekaman setelah layar diputar: ${t.message}")
            return
        }
        // Layar bisa berputar lagi selama pergantian berlangsung
        scheduleOrientationCheck()
    }

    /** Jeda/lanjut dari pill atau notifikasi. @return true jika sekarang dijeda. */
    private fun togglePause(): Boolean {
        val s = seg ?: return paused
        if (finishing || rotating) return paused
        paused = !paused
        s.enc.setPaused(paused)
        island?.syncPaused(paused)
        val np = paused
        safely("perbarui notifikasi") {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(np))
        }
        return paused
    }

    /**
     * Menghentikan rekaman. Bagian cepat berjalan di main thread; finalisasi file (join thread encoder,
     * muxer.stop) di thread latar supaya tidak memicu ANR.
     */
    private fun finish(error: String?) {
        if (finishing) return
        if (rotating) {
            // Tunggu pergantian segmen selesai; onRotated memanggil finish lagi
            pendingFinish = true
            if (pendingError == null) pendingError = error
            return
        }
        if (projection == null && seg == null && outcomes.isEmpty()) {
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
        main.removeCallbacks(orientationCheck)
        safely("lepas pemantau rotasi") {
            getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        }
        safely("sembunyikan pill") { island?.hide() }
        island = null
        val wasRecording = isRecording
        isRecording = false
        notifyState(null)
        val cur = seg
        seg = null
        safely("lepas VirtualDisplay") { display?.release() }
        display = null
        val p = projection
        projection = null
        safely("unregister callback") { p?.unregisterCallback(projectionCb) }
        val earlier = outcomes.toList()
        outcomes.clear()

        Thread({
            val all = earlier.toMutableList()
            if (cur != null) all += finalizeSegment(cur, error, wasRecording)
            safely("hentikan proyeksi") { p?.stop() }

            val saved = all.mapNotNull { it.uri }
            val msg: String = if (saved.isEmpty()) {
                error ?: all.firstNotNullOfOrNull { it.error } ?: "Tidak ada frame yang terekam"
            } else {
                val note = all.firstNotNullOfOrNull { it.note }
                when {
                    error != null -> "$error (${saved.size} file sebelumnya tersimpan)"
                    saved.size > 1 -> "Tersimpan ${saved.size} file di Movies/ScreenRecorder (layar diputar, tiap orientasi jadi file sendiri)"
                    note != null -> "Tersimpan, tapi ada masalah: $note"
                    else -> "Tersimpan di Movies/ScreenRecorder"
                }
            }
            val openUri = saved.lastOrNull()
            main.post { completeFinish(msg, openUri) }
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
        if (!finishing && (projection != null || seg != null)) finish(null)
        super.onDestroy()
    }
}
