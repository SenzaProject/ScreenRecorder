package com.senzaproject.screenrecorder

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * Pemompa frame dengan frame rate KONSTAN.
 * Layar -> [inputSurface] (SurfaceTexture) -> digambar ke surface encoder tepat [fps] kali per detik.
 * Jika layar menghasilkan frame lebih sedikit dari [fps] (layar diam / refresh rate lebih rendah),
 * frame terakhir diulang, sehingga file selalu berisi fps yang dipilih dengan timestamp rata.
 */
class FramePump(
    private val w: Int,
    private val h: Int,
    private val fps: Int,
    private val encSurface: Surface
) {
    @Volatile var inputSurface: Surface? = null
        private set
    @Volatile var paused = false
    @Volatile var framesDrawn = 0L
        private set
    @Volatile var framesFromDisplay = 0L
        private set

    @Volatile private var running = false
    @Volatile private var startNs = 0L
    private val pending = AtomicInteger(0)
    private val ready = CountDownLatch(1)
    private val go = CountDownLatch(1)
    @Volatile private var initError: Throwable? = null
    private var thread: Thread? = null
    private var st: SurfaceTexture? = null

    private val VS = """
        attribute vec4 aPos;
        attribute vec2 aTex;
        uniform mat4 uTex;
        varying vec2 vTex;
        void main() { gl_Position = aPos; vTex = (uTex * vec4(aTex, 0.0, 1.0)).xy; }
    """.trimIndent()
    private val FS = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTex;
        uniform samplerExternalOES sTex;
        void main() { gl_FragColor = texture2D(sTex, vTex); }
    """.trimIndent()

    /** Menyiapkan GL di thread sendiri. Melempar exception jika GL tidak tersedia. */
    fun prepare() {
        thread = Thread({ run() }, "frame-pump").also { it.start() }
        if (!ready.await(5, TimeUnit.SECONDS)) throw IllegalStateException("Inisialisasi GL timeout")
        initError?.let { throw IllegalStateException("GL gagal: ${it.message}", it) }
    }

    /** [baseNs] = System.nanoTime() awal rekaman; timestamp frame dihitung relatif terhadapnya. */
    fun start(baseNs: Long) {
        startNs = baseNs
        running = true
        go.countDown()
    }

    fun stop() {
        running = false
        go.countDown()
        runCatching { thread?.join(2000) }
        runCatching { inputSurface?.release() }
        runCatching { st?.release() }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("Shader: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    private fun floats(vararg v: Float) = ByteBuffer.allocateDirect(v.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }

    private fun run() {
        var dpy: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var ctx: EGLContext = EGL14.EGL_NO_CONTEXT
        var surf: EGLSurface = EGL14.EGL_NO_SURFACE
        var prog = 0
        var tex = 0
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) throw IllegalStateException("eglInitialize")
            val attr = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            if (!EGL14.eglChooseConfig(dpy, attr, 0, cfgs, 0, 1, n, 0) || n[0] == 0)
                throw IllegalStateException("eglChooseConfig")
            ctx = EGL14.eglCreateContext(
                dpy, cfgs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            surf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], encSurface, intArrayOf(EGL14.EGL_NONE), 0)
            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) throw IllegalStateException("eglMakeCurrent")

            prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, compile(GLES20.GL_VERTEX_SHADER, VS))
            GLES20.glAttachShader(prog, compile(GLES20.GL_FRAGMENT_SHADER, FS))
            GLES20.glLinkProgram(prog)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) throw IllegalStateException("Link program")

            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            tex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val s = SurfaceTexture(tex)
            s.setDefaultBufferSize(w, h)
            s.setOnFrameAvailableListener({ pending.incrementAndGet(); framesFromDisplay++ }, Handler(Looper.getMainLooper()))
            st = s
            inputSurface = Surface(s)
        } catch (th: Throwable) {
            initError = th
            ready.countDown()
            cleanup(dpy, ctx, surf, prog, tex)
            return
        }
        ready.countDown()

        try {
            go.await()
            val aPos = GLES20.glGetAttribLocation(prog, "aPos")
            val aTex = GLES20.glGetAttribLocation(prog, "aTex")
            val uTex = GLES20.glGetUniformLocation(prog, "uTex")
            val sTex = GLES20.glGetUniformLocation(prog, "sTex")
            val pos = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val txc = floats(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
            val mtx = FloatArray(16)
            Matrix.setIdentityM(mtx, 0)
            val s = st!!
            val interval = 1_000_000_000L / fps
            var nTick = 0L
            var hasFrame = false

            while (running) {
                val deadline = startNs + nTick * interval
                while (running) {
                    val d = deadline - System.nanoTime()
                    if (d <= 0) break
                    LockSupport.parkNanos(d)
                }
                if (!running) break

                // ambil frame terbaru dari layar (buang yang lebih lama jika ada beberapa)
                val c = pending.getAndSet(0)
                if (c > 0) {
                    try {
                        repeat(c.coerceAtMost(8)) { s.updateTexImage() }
                        s.getTransformMatrix(mtx)
                        hasFrame = true
                    } catch (e: Exception) {}
                }

                if (hasFrame && !paused) {
                    GLES20.glViewport(0, 0, w, h)
                    GLES20.glUseProgram(prog)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
                    GLES20.glUniform1i(sTex, 0)
                    GLES20.glUniformMatrix4fv(uTex, 1, false, mtx, 0)
                    GLES20.glEnableVertexAttribArray(aPos)
                    GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, pos)
                    GLES20.glEnableVertexAttribArray(aTex)
                    GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 8, txc)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    EGLExt.eglPresentationTimeANDROID(dpy, surf, nTick * interval)
                    EGL14.eglSwapBuffers(dpy, surf)
                    framesDrawn++
                }

                nTick++
                // jika tertinggal jauh (encoder lambat), lompat ke waktu sekarang, jangan menumpuk
                val now = System.nanoTime()
                if (now - (startNs + nTick * interval) > 4 * interval) {
                    nTick = (now - startNs) / interval + 1
                }
            }
        } catch (th: Throwable) {
        } finally {
            cleanup(dpy, ctx, surf, prog, tex)
        }
    }

    private fun cleanup(dpy: EGLDisplay, ctx: EGLContext, surf: EGLSurface, prog: Int, tex: Int) {
        runCatching { if (prog != 0) GLES20.glDeleteProgram(prog) }
        runCatching { if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0) }
        if (dpy != EGL14.EGL_NO_DISPLAY) {
            runCatching { EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
            if (surf != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(dpy, surf) }
            if (ctx != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(dpy, ctx) }
        }
        runCatching { EGL14.eglReleaseThread() }
    }
}
