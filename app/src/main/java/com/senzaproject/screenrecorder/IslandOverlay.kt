package com.senzaproject.screenrecorder

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic bar (mengikuti gaya bar perekaman ColorOS):
 *  - Pill hitam ramping: titik merah berdenyut (kiri) + timer merah (kanan), sejajar lubang kamera depan.
 *  - Ketuk pill -> pill "mengembang" jadi kartu "Merekam layar" dengan tombol Pause dan Stop.
 * Pill tidak pernah di-resize saat disentuh; kartu adalah window terpisah.
 */
class IslandOverlay(
    private val ctx: Context,
    /** TYPE_ACCESSIBILITY_OVERLAY (di atas status bar, bisa disentuh) atau TYPE_APPLICATION_OVERLAY. */
    private val windowType: Int,
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

    private var card: LinearLayout? = null
    private var cardTimer: TextView? = null
    private var cardTitle: TextView? = null
    private var cardBars: View? = null
    private var cardPlay: View? = null
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
    private fun dpf(v: Float) = v * ctx.resources.displayMetrics.density

    // ---------- geometri: ramping & simetris terhadap kamera depan ----------
    /** @return [lebar, tinggi, x, y] pill dalam piksel layar. [rects] harus dalam koordinat LAYAR. */
    private fun geometry(rects: List<Rect>?): IntArray {
        val (sw, _) = Caps.screenSize(ctx)
        val h = dp(36)
        val r = rects
            ?.filter { it.top <= dp(2) && it.width() < sw / 2 && it.width() > 0 }
            ?.minByOrNull { abs(it.centerX() - sw / 2) }
        if (r == null) {
            val w = dp(148)
            return intArrayOf(w, h, sw / 2 - w / 2, dp(4))
        }
        val w = max(dp(148), r.width() + dp(28))
        val x = (r.centerX() - w / 2).coerceIn(0, max(0, sw - w))
        val y = max(dp(4), r.centerY() - h / 2)
        return intArrayOf(w, h, x, y)
    }

    /** Cutout dalam koordinat layar (bukan koordinat window pill). */
    private fun screenCutoutRects(): List<Rect>? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30)
            wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
        else null
    }.getOrNull()

    private var cutoutFromInsetsDone = false

    private fun applyGeometry(rects: List<Rect>?) {
        val lp = pillLp ?: return
        val v = pillView ?: return
        val g = geometry(rects)
        if (g[0] == lp.width && g[1] == lp.height && g[2] == lp.x && g[3] == lp.y) return
        lp.width = g[0]; lp.height = g[1]; lp.x = g[2]; lp.y = g[3]
        (v.background as? GradientDrawable)?.cornerRadius = g[1] / 2f
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private inner class PillRoot(c: Context) : FrameLayout(c) {
        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                applyGeometry(screenCutoutRects())
            } else if (!cutoutFromInsetsDone) {
                cutoutFromInsetsDone = true
                val lp = pillLp
                val rects = insets.displayCutout?.boundingRects?.map { Rect(it).apply { offset(lp?.x ?: 0, lp?.y ?: 0) } }
                applyGeometry(rects)
            }
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
                pillDot?.animate()?.alpha(if (dim) 0.3f else 1f)?.setDuration(300)?.start()
            }
            main.postDelayed(this, 1000)
        }
    }
    private val collapseRunnable = Runnable { collapse() }

    private fun timerText(size: Float) = TextView(ctx).apply {
        setTextColor(RED)
        textSize = size
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        includeFontPadding = false
    }

    // ---------- pill ----------
    fun show() {
        if (pillView != null) return
        startMs = SystemClock.elapsedRealtime()
        val g = geometry(screenCutoutRects())

        val root = PillRoot(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = g[1] / 2f }
        }
        val d = View(ctx).apply { background = pillDotBg }
        root.addView(d, FrameLayout.LayoutParams(dp(13), dp(13), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(15) })
        val t = timerText(16f).apply { text = "00:00" }
        root.addView(t, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { rightMargin = dp(14) })
        if (controls) root.setOnClickListener { expand() }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!controls) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val lp = WindowManager.LayoutParams(g[0], g[1], windowType, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = g[2]; y = g[3]
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "ScreenRecorderIsland"
        }
        pillView = root; pillLp = lp; pillTimer = t; pillDot = d
        wm.addView(root, lp)

        root.alpha = 0f
        root.scaleX = 0.5f
        root.animate().alpha(1f).scaleX(1f).setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
        main.postDelayed(tick, 1000)
    }

    // ---------- kartu (diperluas) ----------
    private fun circle(fill: Int?, ring: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (fill != null) setColor(fill)
        if (ring) setStroke(dp(2), Color.parseColor("#F2F2F7"))
    }

    private fun expand() {
        if (card != null || !controls) return
        val (sw, _) = Caps.screenSize(ctx)
        val cardW = min(sw - dp(19), dp(440))
        val cardH = dp(81)
        val pw = pillLp?.width ?: dp(148)
        val ph = pillLp?.height ?: dp(36)

        val row = object : LinearLayout(ctx) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_OUTSIDE) collapse()
                return super.onTouchEvent(e)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(20), 0)
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dpf(40.5f) }
        }

        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(View(ctx).apply { background = cardDotBg }, LinearLayout.LayoutParams(dp(12), dp(12)))
        val ct = timerText(15f).apply { text = fmt(elapsedSec()) }
        top.addView(ct, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
        left.addView(top)
        val titleTv = TextView(ctx).apply {
            text = if (paused) "Dijeda" else "Merekam layar"
            setTextColor(Color.parseColor("#F2F2F7")); textSize = 20f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
        }
        left.addView(titleTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        // tombol pause: lingkaran abu gelap + dua batang putih (atau segitiga saat dijeda)
        val pauseBtn = FrameLayout(ctx).apply { background = circle(Color.parseColor("#2C2C2E"), false) }
        val bars = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for (i in 0..1) {
            bars.addView(
                View(ctx).apply { background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dpf(2f) } },
                LinearLayout.LayoutParams(dp(5), dp(19)).apply { if (i == 1) leftMargin = dp(5) }
            )
        }
        val play = TextView(ctx).apply {
            text = "▶"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER; visibility = View.GONE
        }
        pauseBtn.addView(bars, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        pauseBtn.addView(play, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        pauseBtn.setOnClickListener {
            main.removeCallbacks(collapseRunnable)
            main.postDelayed(collapseRunnable, 6000)
            setPausedUi(onTogglePause())
        }
        row.addView(pauseBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

        // tombol stop: cincin putih + kotak merah
        val stopBtn = FrameLayout(ctx).apply { background = circle(null, true) }
        stopBtn.addView(
            View(ctx).apply { background = GradientDrawable().apply { setColor(Color.parseColor("#FF453A")); cornerRadius = dpf(5f) } },
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        )
        stopBtn.setOnClickListener { collapse(); onStop() }
        row.addView(stopBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(17) })

        val lp = WindowManager.LayoutParams(
            cardW, cardH, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = pillLp?.y ?: dp(4)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "ScreenRecorderIslandCard"
        }
        card = row; cardTimer = ct; cardTitle = titleTv; cardBars = bars; cardPlay = play
        setPausedUi(paused)
        wm.addView(row, lp)

        // pill "mengembang" menjadi kartu: bentuk membesar dulu, isi muncul menyusul
        val pillCenterX = (pillLp?.x ?: 0) + pw / 2f
        row.pivotX = cardW / 2f
        row.pivotY = 0f
        row.scaleX = pw.toFloat() / cardW
        row.scaleY = ph.toFloat() / cardH
        row.translationX = pillCenterX - sw / 2f
        for (i in 0 until row.childCount) row.getChildAt(i).alpha = 0f
        val startAnim = {
            row.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(380)
                .setInterpolator(OvershootInterpolator(0.5f)).start()
            for (i in 0 until row.childCount) {
                row.getChildAt(i).animate().alpha(1f).setStartDelay(140).setDuration(200).start()
            }
        }
        row.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                row.viewTreeObserver.removeOnPreDrawListener(this)
                startAnim()
                return true
            }
        })
        main.postDelayed(collapseRunnable, 6000)
    }

    private fun collapse() {
        main.removeCallbacks(collapseRunnable)
        val c = card ?: return
        card = null; cardTimer = null; cardTitle = null; cardBars = null; cardPlay = null
        val (sw, _) = Caps.screenSize(ctx)
        val pw = pillLp?.width ?: dp(148)
        val ph = pillLp?.height ?: dp(36)
        val pillCenterX = (pillLp?.x ?: 0) + pw / 2f
        for (i in 0 until c.childCount) c.getChildAt(i).animate().alpha(0f).setStartDelay(0).setDuration(100).start()
        c.animate().scaleX(pw.toFloat() / max(1, c.width)).scaleY(ph.toFloat() / max(1, c.height))
            .translationX(pillCenterX - sw / 2f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                runCatching { wm.removeView(c) }
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
        cardBars?.visibility = if (p) View.GONE else View.VISIBLE
        cardPlay?.visibility = if (p) View.VISIBLE else View.GONE
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
