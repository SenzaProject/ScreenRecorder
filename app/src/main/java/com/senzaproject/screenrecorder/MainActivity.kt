package com.senzaproject.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private lateinit var spRes: Spinner
    private lateinit var spFps: Spinner
    private lateinit var swDev: Switch
    private lateinit var swMic: Switch
    private lateinit var btn: Button
    private lateinit var tvInfo: TextView
    private var resList = listOf<Caps.Res>()
    private var fpsList = listOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        spRes = findViewById(R.id.spRes)
        spFps = findViewById(R.id.spFps)
        swDev = findViewById(R.id.swDev)
        swMic = findViewById(R.id.swMic)
        btn = findViewById(R.id.btn)
        tvInfo = findViewById(R.id.tvInfo)

        resList = Caps.available(this)
        spRes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resList.map { it.label })
        spRes.setSelection(resList.indexOfFirst { it.shortSide == 1080 }.coerceAtLeast(0))
        spRes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateFps()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        updateFps()

        val hz = windowManager.defaultDisplay.refreshRate.toInt()
        val k2 = if (Caps.supports2K(this)) "2K 120fps didukung perangkat ini." else "2K 120fps tidak didukung perangkat ini."
        tvInfo.text = "$k2\nRefresh rate layar: $hz Hz. Frame unik tidak bisa melebihi refresh rate layar."

        btn.setOnClickListener { if (RecorderService.isRecording) stopRec() else begin() }
    }

    override fun onResume() {
        super.onResume()
        RecorderService.listener = { _, msg ->
            refresh()
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    override fun onPause() {
        RecorderService.listener = null
        super.onPause()
    }

    private fun updateFps() {
        val res = resList[spRes.selectedItemPosition]
        fpsList = Caps.fpsFor(res)
        spFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fpsList.map { "$it fps" })
        spFps.setSelection(fpsList.lastIndex.coerceAtMost(1))
    }

    private fun refresh() {
        val rec = RecorderService.isRecording
        btn.text = if (rec) "Stop Rekam" else "Mulai Rekam"
        spRes.isEnabled = !rec
        spFps.isEnabled = !rec
        swDev.isEnabled = !rec
        swMic.isEnabled = !rec
    }

    private fun begin() {
        val need = mutableListOf<String>()
        if ((swMic.isChecked || swDev.isChecked) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else askProjection()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((swMic.isChecked || swDev.isChecked) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            swMic.isChecked = false
            swDev.isChecked = false
            Toast.makeText(this, "Izin audio ditolak, merekam tanpa suara", Toast.LENGTH_SHORT).show()
        }
        askProjection()
    }

    private fun askProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Izin perekaman layar ditolak", Toast.LENGTH_SHORT).show()
            return
        }
        val res = resList[spRes.selectedItemPosition]
        val fps = fpsList[spFps.selectedItemPosition]
        val (w, h) = Caps.dims(this, res.shortSide)
        val i = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra("code", resultCode)
            putExtra("data", data)
            putExtra("w", w)
            putExtra("h", h)
            putExtra("fps", fps)
            putExtra("dpi", resources.displayMetrics.densityDpi)
            putExtra("dev", swDev.isChecked)
            putExtra("mic", swMic.isChecked)
        }
        startForegroundService(i)
        // Kembali ke layar utama agar layar yang direkam adalah konten lain
        moveTaskToBack(true)
    }

    private fun stopRec() {
        startService(Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
    }
}
