package com.senzaproject.screenrecorder

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Pill ala Dynamic Island: titik merah + timer.
 * Sengaja TIDAK bisa disentuh dan ukurannya tetap, supaya tidak mengganggu perekaman layar.
 * Untuk berhenti merekam gunakan tombol Stop di notifikasi atau tombol di aplikasi.
 */
class IslandOverlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var timer: TextView? = null
    private var dot: View? = null
    private var startMs = 0L
    private var dim = false

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    // Update 1x per detik dan denyut titik hanya 2x per siklus: tidak memaksa layar menggambar terus-menerus
    private val tick = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - startMs) / 1000
            timer?.text = if (s >= 3600)
                String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
            else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
            dim = !dim
            dot?.animate()?.alpha(if (dim) 0.35f else 1f)?.setDuration(300)?.start()
            main.postDelayed(this, 1000)
        }
    }

    fun show() {
        if (root != null) return
        startMs = SystemClock.elapsedRealtime()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(20).toFloat()
            }
        }
        val d = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF4B42")) }
        }
        row.addView(d, LinearLayout.LayoutParams(dp(10), dp(10)))
        val t = TextView(ctx).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            minWidth = dp(52)
            gravity = Gravity.END
        }
        row.addView(
            t,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = dp(36) }
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, dp(36),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(10)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "ScreenRecorderIsland"
        }

        root = row; timer = t; dot = d
        wm.addView(row, lp)

        row.alpha = 0f
        row.scaleX = 0.4f
        row.translationY = -dp(16).toFloat()
        row.animate().alpha(1f).scaleX(1f).translationY(0f)
            .setDuration(450).setInterpolator(OvershootInterpolator(1.2f)).start()
        main.postDelayed(tick, 1000)
    }

    fun hide() {
        main.removeCallbacks(tick)
        val r = root ?: return
        root = null
        r.animate().alpha(0f).setDuration(150).withEndAction {
            runCatching { wm.removeView(r) }
        }.start()
    }
}
