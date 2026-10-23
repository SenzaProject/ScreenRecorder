package com.senzaproject.screenrecorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Layanan kosong. Fungsinya hanya menyediakan window bertipe TYPE_ACCESSIBILITY_OVERLAY
 * yang berada di atas status bar, sehingga pill bisa disentuh. Tidak membaca isi layar.
 */
class IslandAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: IslandAccessibilityService? = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
}
