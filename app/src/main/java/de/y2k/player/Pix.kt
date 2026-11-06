package de.y2k.player

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Farb-Helfer: Farben sind RGB-Ints (0xRRGGBB), Deckkraft wird separat behandelt. */
object Col {
    fun r(c: Int) = (c shr 16) and 255
    fun g(c: Int) = (c shr 8) and 255
    fun b(c: Int) = c and 255
    fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return rgb((r(a) + (r(b) - r(a)) * k).roundToInt(), (g(a) + (g(b) - g(a)) * k).roundToInt(), (b(a) + (b(b) - b(a)) * k).roundToInt())
    }
    fun scale(c: Int, s: Float) = rgb((r(c) * s).toInt(), (g(c) * s).toInt(), (b(c) * s).toInt())
    fun argb(c: Int, a: Int = 255) = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
    fun parse(hex: String) = hex.removePrefix("#").toInt(16)
    const val WHITE = 0xFFFFF0
    /** Farbton drehen (für "Regenbogen"). */
    fun hue(c: Int, deg: Float): Int {
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(argb(c), hsv)
        hsv[0] = (hsv[0] + deg) % 360f
        return android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF
    }
}

/** 5x7-Pixelschrift */
object Font {
    private val SRC = mapOf(
        'A' to "01110 10001 10001 11111 10001 10001 10001", 'B' to "11110 10001 10001 11110 10001 10001 11110",
        'C' to "01110 10001 10000 10000 10000 10001 01110", 'D' to "11100 10010 10001 10001 10001 10010 11100",
        'E' to "11111 10000 10000 11110 10000 10000 11111", 'F' to "11111 10000 10000 11110 10000 10000 10000",
        'G' to "01110 10001 10000 10111 10001 10001 01111", 'H' to "10001 10001 10001 11111 10001 10001 10001",
        'I' to "01110 00100 00100 00100 00100 00100 01110", 'J' to "00111 00010 00010 00010 00010 10010 01100",
        'K' to "10001 10010 10100 11000 10100 10010 10001", 'L' to "10000 10000 10000 10000 10000 10000 11111",
        'M' to "10001 11011 10101 10101 10001 10001 10001", 'N' to "10001 10001 11001 10101 10011 10001 10001",
        'O' to "01110 10001 10001 10001 10001 10001 01110", 'P' to "11110 10001 10001 11110 10000 10000 10000",
        'Q' to "01110 10001 10001 10001 10101 10010 01101", 'R' to "11110 10001 10001 11110 10100 10010 10001",
        'S' to "01111 10000 10000 01110 00001 00001 11110", 'T' to "11111 00100 00100 00100 00100 00100 00100",
        'U' to "10001 10001 10001 10001 10001 10001 01110", 'V' to "10001 10001 10001 10001 10001 01010 00100",
        'W' to "10001 10001 10001 10101 10101 10101 01010", 'X' to "10001 10001 01010 00100 01010 10001 10001",
        'Y' to "10001 10001 10001 01010 00100 00100 00100", 'Z' to "11111 00001 00010 00100 01000 10000 11111",
        '0' to "01110 10001 10011 10101 11001 10001 01110", '1' to "00100 01100 00100 00100 00100 00100 01110",
        '2' to "01110 10001 00001 00010 00100 01000 11111", '3' to "11111 00010 00100 00010 00001 10001 01110",
        '4' to "00010 00110 01010 10010 11111 00010 00010", '5' to "11111 10000 11110 00001 00001 10001 01110",
        '6' to "00110 01000 10000 11110 10001 10001 01110", '7' to "11111 00001 00010 00100 01000 01000 01000",
        '8' to "01110 10001 10001 01110 10001 10001 01110", '9' to "01110 10001 10001 01111 00001 00010 01100",
        '&' to "01100 10010 10100 01000 10101 10010 01101", '-' to "00000 00000 00000 11111 00000 00000 00000",
        '.' to "00000 00000 00000 00000 00000 01100 01100", '/' to "00001 00010 00010 00100 01000 01000 10000",
        ':' to "00000 01100 01100 00000 01100 01100 00000", '·' to "00000 00000 00000 01100 00000 00000 00000",
        '+' to "00000 00100 00100 11111 00100 00100 00000", '\'' to "00100 00100 01000 00000 00000 00000 00000",
        '(' to "00010 00100 01000 01000 01000 00100 00010", ')' to "01000 00100 00010 00010 00010 00100 01000",
        '!' to "00100 00100 00100 00100 00100 00000 00100", '?' to "01110 10001 00001 00010 00100 00000 00100",
        '_' to "00000 00000 00000 00000 00000 00000 11111", '#' to "01010 11111 01010 01010 11111 01010 00000",
        ' ' to "00000 00000 00000 00000 00000 00000 00000",
    )
    private val GLYPH: Map<Char, Array<BooleanArray>> = SRC.mapValues { (_, v) -> v.split(" ").map { r -> BooleanArray(5) { r[it] == '1' } }.toTypedArray() }
    private val ALIAS = mapOf('Ä' to 'A', 'Ö' to 'O', 'Ü' to 'U', 'ß' to 'S', 'É' to 'E', 'È' to 'E', 'Á' to 'A', 'À' to 'A', 'Ó' to 'O', 'Í' to 'I', 'Ñ' to 'N', 'Ç' to 'C')
    fun glyph(ch: Char): Array<BooleanArray> { val u = ch.uppercaseChar(); return GLYPH[u] ?: GLYPH[ALIAS[u] ?: ' '] ?: GLYPH.getValue(' ') }
    fun adv(ch: Char) = if (ch == ' ') 3 else 6
    fun width(s: String, sc: Int = 1) = (s.sumOf { adv(it) } - 1) * sc
}

/** Kleiner Pixelpuffer, wird mit "Nächster Nachbar" hochskaliert gezeichnet. */
class Pix(val w: Int, val h: Int) {
    val px = IntArray(w * h)
    val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    fun clear(c: Int) = px.fill(Col.argb(c))
    fun set(x: Float, y: Float, c: Int, s: Float = 1f) = set(x.toInt(), y.toInt(), c, s)
    fun set(x: Int, y: Int, c: Int, s: Float = 1f) {
        if (x < 0 || y < 0 || x >= w || y >= h || s <= 0f) return
        px[y * w + x] = if (s >= .999f && s <= 1.001f) Col.argb(c) else Col.argb(Col.scale(c, s))
    }
    fun get(x: Int, y: Int) = px[y * w + x] and 0xFFFFFF
    fun fade(f: Float, bg: Int) {
        val br = Col.r(bg); val bgc = Col.g(bg); val bb = Col.b(bg)
        for (i in px.indices) {
            val c = px[i]
            px[i] = Col.argb(Col.rgb(br + ((Col.r(c) - br) * f).toInt(), bgc + ((Col.g(c) - bgc) * f).toInt(), bb + ((Col.b(c) - bb) * f).toInt()))
        }
    }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, s: Float = 1f) {
        val n = max(1, max(abs(x1 - x0), abs(y1 - y0)).toInt())
        for (i in 0..n) set(x0 + (x1 - x0) * i / n + .5f, y0 + (y1 - y0) * i / n + .5f, c, s)
    }
    fun text(str: String, x0: Int, y: Int, sc: Int, c: Int, s: Float = 1f, mask: ((Int, Int) -> Boolean)? = null) {
        var x = x0
        for (ch in str) {
            val g = Font.glyph(ch)
            for (k in 0 until 7) for (j in 0 until 5) if (g[k][j])
                for (a in 0 until sc) for (b in 0 until sc) {
                    val X = x + j * sc + b; val Y = y + k * sc + a
                    if (mask == null || mask(X, Y)) set(X, Y, c, s)
                }
            x += Font.adv(ch) * sc
        }
    }
    fun flush() = bmp.setPixels(px, 0, w, 0, 0, w, h)
    fun shiftUp() { System.arraycopy(px, w, px, 0, w * (h - 1)) }
}

/** Gemeinsame Mathe-Helfer */
fun clamp01(v: Float) = min(1f, max(0f, v))
fun frac(v: Float) = v - kotlin.math.floor(v)
private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
fun dith(x: Int, y: Int) = BAYER[(y and 3) * 4 + (x and 3)] / 16f - .5f
