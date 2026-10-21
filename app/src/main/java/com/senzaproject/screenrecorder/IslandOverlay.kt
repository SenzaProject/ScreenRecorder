package com.senzaproject.screenrecorder

import android.animation.ValueAnimator
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

/** Pill ala Dynamic Island di atas layar: titik merah berdenyut + timer. Ketuk untuk memunculkan tombol Stop. */
class IslandOverlay(private val ctx: Context, private val onStop: () -> Unit) {

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var timer: TextView? = null
    private var stopBtn: TextView? = null
    private var dot: View? = null
    private var pulse: ValueAnimator? = null
    private var startMs = 0L

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val tick = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - startMs) / 1000
            timer?.text = if (s >= 3600)
                String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
            else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
            main.postDelayed(this, 250)
        }
    }
    private val collapse = Runnable { setExpanded(false) }

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
        }
        row.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { leftMargin = dp(44) })
        val sb = TextView(ctx).apply {
            text = "■  Stop"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply { setColor(Color.parseColor("#E5231F")); cornerRadius = dp(14).toFloat() }
            visibility = View.GONE
            setOnClickListener { onStop() }
        }
        row.addView(sb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { leftMargin = dp(14) })
        row.setOnClickListener { setExpanded(sb.visibility != View.VISIBLE) }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, dp(36),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(10)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "ScreenRecorderIsland"
        }

        root = row; timer = t; stopBtn = sb; dot = d
        wm.addView(row, lp)

        // animasi masuk ala Dynamic Island
        row.alpha = 0f
        row.scaleX = 0.4f
        row.translationY = -dp(16).toFloat()
        row.animate().alpha(1f).scaleX(1f).translationY(0f)
            .setDuration(450).setInterpolator(OvershootInterpolator(1.2f)).start()

        // titik merah berdenyut
        pulse = ValueAnimator.ofFloat(1f, 0.35f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                d.alpha = v
                d.scaleX = 0.8f + 0.2f * v
                d.scaleY = 0.8f + 0.2f * v
            }
            start()
        }
        main.post(tick)
    }

    private fun setExpanded(e: Boolean) {
        val sb = stopBtn ?: return
        main.removeCallbacks(collapse)
        if (e) {
            sb.alpha = 0f
            sb.visibility = View.VISIBLE
            sb.animate().alpha(1f).setDuration(200).start()
            main.postDelayed(collapse, 4000)
        } else {
            sb.visibility = View.GONE
        }
        root?.requestLayout()
    }

    fun hide() {
        main.removeCallbacks(tick)
        main.removeCallbacks(collapse)
        pulse?.cancel()
        pulse = null
        val r = root ?: return
        root = null
        r.animate().alpha(0f).scaleX(0.4f).setDuration(200).withEndAction {
            runCatching { wm.removeView(r) }
        }.start()
    }
}
