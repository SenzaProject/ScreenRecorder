package com.senzaproject.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsTest {

    @Test
    fun bitrate_1080p30_mengikuti_rumus() {
        // 1920 * 1080 * 30 * 0.20 = 12.441.600 bps
        assertEquals(12_441_600, Caps.bitrateFor(1920, 1080, 30))
    }

    @Test
    fun bitrate_tidak_di_bawah_batas_minimum() {
        assertEquals(3_000_000, Caps.bitrateFor(320, 240, 30))
    }

    @Test
    fun bitrate_tidak_melebihi_batas_maksimum() {
        assertEquals(100_000_000, Caps.bitrateFor(7680, 4320, 120))
    }

    @Test
    fun bitrate_naik_bersama_resolusi() {
        assertTrue(Caps.bitrateFor(1920, 1080, 60) > Caps.bitrateFor(1280, 720, 60))
    }

    @Test
    fun fps_2k_hanya_120() {
        assertEquals(listOf(120), Caps.fpsFor(Caps.Res("2K", 1440)))
    }

    @Test
    fun fps_1080p_menyediakan_semua_pilihan() {
        assertEquals(listOf(30, 60, 90, 120), Caps.fpsFor(Caps.Res("1080p", 1080)))
    }

    // ---- ukuran video mengikuti orientasi (perbaikan: game landscape tidak lagi mengecil di frame portrait) ----

    @Test
    fun ukuran_portrait_dan_landscape_sesuai_orientasi_layar() {
        assertEquals(1080 to 2400, Caps.dimsFor(1080, 2400, 1080, 2, 2))
        assertEquals(2400 to 1080, Caps.dimsFor(2400, 1080, 1080, 2, 2))
    }

    @Test
    fun landscape_selalu_lebih_lebar_dari_tinggi_dan_portrait_sebaliknya() {
        for ((sw, sh) in listOf(1080 to 2400, 1264 to 2780, 1440 to 3200, 720 to 1600)) {
            for (short in listOf(480, 720, 1080, 1440)) {
                val p = Caps.dimsFor(sw, sh, short, 16, 16)
                val l = Caps.dimsFor(sh, sw, short, 16, 16)
                assertTrue("portrait $sw x $sh @$short", p.first < p.second)
                assertTrue("landscape $sh x $sw @$short", l.first > l.second)
                // Memutar layar hanya menukar lebar dan tinggi video
                assertEquals(p.second to p.first, l)
            }
        }
    }

    @Test
    fun alignment_encoder_dihormati_di_kedua_orientasi() {
        assertEquals(1088 to 2368, Caps.dimsFor(1264, 2780, 1080, 16, 16))
        assertEquals(2368 to 1088, Caps.dimsFor(2780, 1264, 1080, 16, 16))
    }

    @Test
    fun alignment_lebar_dan_tinggi_berbeda_mengikuti_sumbu_yang_benar() {
        // lebar kelipatan 16, tinggi kelipatan 2
        assertEquals(2368 to 1080, Caps.dimsFor(2780, 1264, 1080, 16, 2))
        assertEquals(1088 to 2376, Caps.dimsFor(1264, 2780, 1080, 16, 2))
    }

    @Test
    fun ukuran_2k_ikut_orientasi() {
        assertEquals(1440 to 3200, Caps.dimsFor(1440, 3200, 1440, 2, 2))
        assertEquals(3200 to 1440, Caps.dimsFor(3200, 1440, 1440, 2, 2))
    }
}
