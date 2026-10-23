package com.senza.spphotos

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

data class FontDef(val label: String, val file: String)

/** 14 font yang dibundel di assets/fonts (berlisensi bebas, lihat LICENSES.txt). */
val EDITOR_FONTS = listOf(
    FontDef("Poppins Light", "poppins_light.ttf"),
    FontDef("Poppins", "poppins_regular.ttf"),
    FontDef("Poppins Bold", "poppins_bold.ttf"),
    FontDef("Poppins Italic", "poppins_bolditalic.ttf"),
    FontDef("Lora", "lora.ttf"),
    FontDef("Baskerville", "gfs_baskerville.otf"),
    FontDef("Caladea", "caladea_bold.ttf"),
    FontDef("Carlito", "carlito_bold.ttf"),
    FontDef("Liberation Sans", "liberation_sans_bold.ttf"),
    FontDef("Liberation Serif", "liberation_serif_bold.ttf"),
    FontDef("Condensed", "dejavu_condensed_bold.ttf"),
    FontDef("DejaVu Serif", "dejavu_serif_bold.ttf"),
    FontDef("Mono", "dejavu_mono.ttf"),
    FontDef("Mono Bold", "liberation_mono_bold.ttf")
)

object FontStore {
    private val cache = HashMap<Int, Typeface>()

    fun get(ctx: Context, index: Int): Typeface {
        if (index < 0 || index >= EDITOR_FONTS.size) return Typeface.DEFAULT
        return cache.getOrPut(index) {
            try {
                Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/" + EDITOR_FONTS[index].file)
            } catch (e: Throwable) {
                Typeface.DEFAULT
            }
        }
    }

    fun family(ctx: Context, index: Int): FontFamily = FontFamily(get(ctx, index))
}
