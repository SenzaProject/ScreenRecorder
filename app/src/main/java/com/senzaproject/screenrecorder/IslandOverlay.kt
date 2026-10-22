package com.senzaproject.screenrecorder

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.DisplayCutout
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic bar:
 *  - Pill hitam berisi titik merah + timer, otomatis sejajar (simetris) dengan lubang kamera depan.
 *  - Ketuk pill -> mengembang jadi kartu "Merekam layar" dengan tombol Pause dan Stop.
 * Ukuran pill tetap; kartu adalah window terpisah (pill tidak pernah di-resize saat disentuh).
 */
class IslandOverlay(
    private val ctx: Context,
    private val controls: Boolean,
    /** Dipanggil saat tombol Pause/Resume ditekan. Mengembalikan status dijeda yang baru. */
    private val onTogglePause: () -> Boolean,
    private val onStop: () -> Unit
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var pillView: PillRoot? = null
    private var pillLp: WindowManager.LayoutParams? = null
    private var pillTimer: TextView? = null
    private var pillDot: View? = null
    private val pillDotBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED) }

    private var card: View? = null
    private var cardTimer: TextView? = null
    private var cardTitle: TextView? = null
    private var cardPauseIcon: TextView? = null
    private val cardDotBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED) }

    private var startMs = 0L
    private var pausedAt = 0L
    private var accPaused = 0L
    private var paused = false
    private var dim = false

    companion object {
        private val RED = Color.parseColor("#FF4B42")
        private val GRAY = Color.parseColor("#8E8E93")
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    // ---------- geometri: simetris terhadap kamera depan ----------
    /** @return [lebar, tinggi, x, y] pill dalam piksel layar */
    private fun geometry(cutout: DisplayCutout?): IntArray {
        val (sw, _) = Caps.screenSize(ctx)
        val r = cutout?.boundingRects
            ?.filter { it.top <= dp(2) && it.width() < sw / 2 && it.width() > 0 }
            ?.minByOrNull { abs(it.centerX() - sw / 2) }
        if (r == null) {
            val w = dp(124); val h = dp(34)
            return intArrayOf(w, h, sw / 2 - w / 2, dp(10))
        }
        val h = max(dp(34), r.height() + dp(12))
        val w = max(dp(124), r.width() + dp(92))
        val x = (r.centerX() - w / 2).coerceIn(0, max(0, sw - w))
        val y = max(0, r.centerY() - h / 2)
        return intArrayOf(w, h, x, y)
    }

    private fun applyCutout(c: DisplayCutout?) {
        val lp = pillLp ?: return
        val v = pillView ?: return
        val g = geometry(c)
        if (g[0] == lp.width && g[1] == lp.height && g[2] == lp.x && g[3] == lp.y) return
        lp.width = g[0]; lp.height = g[1]; lp.x = g[2]; lp.y = g[3]
        (v.background as? GradientDrawable)?.cornerRadius = g[1] / 2f
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private inner class PillRoot(c: Context) : FrameLayout(c) {
        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            applyCutout(insets.displayCutout)
            return super.onApplyWindowInsets(insets)
        }
    }

    // ---------- timer ----------
    private fun elapsedSec(): Long {
        val now = SystemClock.elapsedRealtime()
        val ref = if (paused) pausedAt else now
        return max(0L, (ref - startMs - accPaused) / 1000)
    }

    private fun fmt(s: Long) =
        if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)

    // Update 1x per detik supaya layar tidak digambar ulang terus-menerus
    private val tick = object : Runnable {
        override fun run() {
            val t = fmt(elapsedSec())
            pillTimer?.text = t
            cardTimer?.text = t
            if (!paused) {
                dim = !dim
                pillDot?.animate()?.alpha(if (dim) 0.35f else 1f)?.setDuration(300)?.start()
            }
            main.postDelayed(this, 1000)
        }
    }
    private val collapseRunnable = Runnable { collapse() }

    // ---------- pill ----------
    fun show() {
        if (pillView != null) return
        startMs = SystemClock.elapsedRealtime()

        val initialCutout = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.windowInsets.displayCutout else null
        }.getOrNull()
        val g = geometry(initialCutout)

        val root = PillRoot(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = g[1] / 2f }
        }
        val d = View(ctx).apply { background = pillDotBg }
        root.addView(d, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(16) })
        val t = TextView(ctx).apply {
            text = "00:00"
            setTextColor(RED)
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        root.addView(t, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { rightMargin = dp(16) })
        if (controls) root.setOnClickListener { expand() }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!controls) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val lp = WindowManager.LayoutParams(
            g[0], g[1], WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = g[2]; y = g[3]
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "ScreenRecorderIsland"
        }
        pillView = root; pillLp = lp; pillTimer = t; pillDot = d
        wm.addView(root, lp)

        root.alpha = 0f
        root.scaleX = 0.4f
        root.animate().alpha(1f).scaleX(1f).setDuration(450).setInterpolator(OvershootInterpolator(1.2f)).start()
        main.postDelayed(tick, 1000)
    }

    // ---------- kartu (diperluas) ----------
    private fun circle(fill: Int?, ring: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (fill != null) setColor(fill)
        if (ring) setStroke(dp(2), Color.WHITE)
    }

    private fun expand() {
        if (card != null || !controls) return
        val (sw, _) = Caps.screenSize(ctx)
        val cardW = min(sw - dp(24), dp(380))
        val cardH = dp(68)

        val row = object : LinearLayout(ctx) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_OUTSIDE) collapse()
                return super.onTouchEvent(e)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(14), 0)
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(34).toFloat() }
        }

        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(View(ctx).apply { background = cardDotBg }, LinearLayout.LayoutParams(dp(8), dp(8)))
        val ct = TextView(ctx).apply {
            text = fmt(elapsedSec()); setTextColor(RED); textSize = 13f; typeface = Typeface.MONOSPACE
        }
        top.addView(ct, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
        left.addView(top)
        val title = TextView(ctx).apply {
            text = if (paused) "Dijeda" else "Merekam layar"
            setTextColor(Color.WHITE); textSize = 16f
        }
        left.addView(title)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val pauseBtn = FrameLayout(ctx).apply { background = circle(Color.parseColor("#3A3A3C"), false) }
        val pauseIcon = TextView(ctx).apply {
            text = if (paused) "▶" else "❚❚"; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
        }
        pauseBtn.addView(pauseIcon, FrameLayout.LayoutParams(-1, -1))
        pauseBtn.setOnClickListener {
            main.removeCallbacks(collapseRunnable)
            main.postDelayed(collapseRunnable, 6000)
            setPausedUi(onTogglePause())
        }
        row.addView(pauseBtn, LinearLayout.LayoutParams(dp(42), dp(42)))

        val stopBtn = FrameLayout(ctx).apply { background = circle(null, true) }
        stopBtn.addView(
            View(ctx).apply { background = GradientDrawable().apply { setColor(RED); cornerRadius = dp(3).toFloat() } },
            FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
        )
        stopBtn.setOnClickListener { collapse(); onStop() }
        row.addView(stopBtn, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(10) })

        val lp = WindowManager.LayoutParams(
            cardW, cardH, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = pillLp?.y ?: dp(10)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            this.title = "ScreenRecorderIslandCard"
        }
        card = row; cardTimer = ct; cardTitle = title; cardPauseIcon = pauseIcon
        setPausedUi(paused)
        wm.addView(row, lp)

        pillView?.visibility = View.INVISIBLE
        row.pivotY = 0f
        row.alpha = 0f
        row.scaleX = (pillLp?.width ?: dp(124)).toFloat() / cardW
        row.scaleY = 0.5f
        row.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(0.9f)).start()
        main.postDelayed(collapseRunnable, 6000)
    }

    private fun collapse() {
        main.removeCallbacks(collapseRunnable)
        val c = card ?: return
        card = null; cardTimer = null; cardTitle = null; cardPauseIcon = null
        c.animate().alpha(0f).scaleX(0.7f).setDuration(150).withEndAction {
            runCatching { wm.removeView(c) }
            pillView?.visibility = View.VISIBLE
        }.start()
    }

    private fun setPausedUi(p: Boolean) {
        if (p != paused) {
            val now = SystemClock.elapsedRealtime()
            if (p) pausedAt = now else accPaused += now - pausedAt
            paused = p
        }
        val color = if (p) GRAY else RED
        pillDotBg.setColor(color); cardDotBg.setColor(color)
        pillDot?.alpha = 1f
        cardPauseIcon?.text = if (p) "▶" else "❚❚"
        cardTitle?.text = if (p) "Dijeda" else "Merekam layar"
    }

    fun hide() {
        main.removeCallbacks(tick)
        main.removeCallbacks(collapseRunnable)
        card?.let { c -> card = null; runCatching { wm.removeView(c) } }
        val r = pillView ?: return
        pillView = null
        r.animate().alpha(0f).setDuration(150).withEndAction { runCatching { wm.removeView(r) } }.start()
    }
}
