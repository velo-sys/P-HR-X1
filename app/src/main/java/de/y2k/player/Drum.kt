package de.y2k.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Pixel-Walze mit allen Ordnern (Nummer + Liedzahl in Zweitfarbe, Name in Hauptfarbe). */
class Drum {
    val pix = Pix(140, 34)
    var angle = 0f; private var vel = 0f; private var target = 0f; private var idx = 0
    var mode = 0
    var dirty = true
    private val R = 30f

    fun jump(i: Int) { idx = i; target = i.toFloat(); angle = target; vel = 0f; dirty = true }
    fun set(i: Int, n: Int) {
        if (n <= 0) return
        var d = ((i - idx) % n + n) % n; if (d > n / 2) d -= n
        target += d; idx = i; dirty = true
    }

    fun step() {
        val diff = target - angle
        if (abs(diff) < .002f && abs(vel) < .01f) { if (angle != target) { angle = target; vel = 0f; dirty = true }; return }
        dirty = true
        when (mode) {
            3 -> { angle = target; vel = 0f }
            2 -> angle += sign(diff) * min(.34f, abs(diff))
            else -> { val (k, c) = if (mode == 1) 90f to 19f else 160f to 16f; val dt = .05f; vel += (k * diff - c * vel) * dt; angle += vel * dt }
        }
    }

    private var lastVc = -1; private var lastV2 = -1; private var lastN = -1
    fun draw(folders: List<Folder>, vc: Int, v2: Int) {
        if (vc != lastVc || v2 != lastV2 || folders.size != lastN) { lastVc = vc; lastV2 = v2; lastN = folders.size; dirty = true }   // Farbschema-Wechsel
        if (!dirty) return; dirty = false
        val n = folders.size
        pix.clear(Col.scale(vc, .03f))
        if (n == 0) { pix.text("KEINE MUSIK GEFUNDEN", 10, 13, 1, vc, .8f); pix.flush(); return }
        val bg = Col.scale(vc, .16f); val cy = pix.h / 2f
        val ang = if (mode == 2) (angle * 4).roundToInt() / 4f else angle
        for (i in 0 until n) {
            // feste Flächenhöhe (12 Plätze pro Umdrehung) – bei vielen Ordnern sieht man nur die Nachbarn
            val faceStep = (2 * PI / 12).toFloat()
            val thv = (i - ang).let { var d = it % n; if (d > n / 2f) d -= n; if (d < -n / 2f) d += n; d } * faceStep
            if (abs(thv) > PI / 2) continue
            val cs = cos(thv); if (cs < .08f) continue
            val yc = cy + R * sin(thv)
            val top = (cy + R * sin(thv - faceStep / 2)).roundToInt(); val bot = (cy + R * sin(thv + faceStep / 2)).roundToInt()
            val sh = .15f + .85f * cs.pow(1.4f); val front = abs(thv) < faceStep / 2
            for (y in top until bot) for (x in 1 until pix.w - 1) pix.set(x, y, bg, sh * (if (y == top) .35f else 1f))
            val cMain = if (front) Col.mix(vc, Col.WHITE, .25f) else vc
            val cSec = if (front) Col.mix(v2, Col.WHITE, .25f) else v2
            row("%02d %dT".format(i + 1, folders[i].tracks.size), 5, yc, cs, cSec, sh * .9f)
            row(folders[i].name.uppercase().take(15), 44, yc, cs, cMain, sh)
        }
        pix.flush()
    }

    private fun row(s: String, x0: Int, yc: Float, cs: Float, col: Int, sh: Float) {
        var x = x0
        for (ch in s) { val g = Font.glyph(ch)
            for (k in 0 until 7) { val y = (yc + (k - 3) * cs).roundToInt(); for (j in 0 until 5) if (g[k][j]) pix.set(x + j, y, col, sh) }
            x += Font.adv(ch) }
    }
}
