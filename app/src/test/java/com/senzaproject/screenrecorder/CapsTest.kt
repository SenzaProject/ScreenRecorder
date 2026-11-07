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
}
