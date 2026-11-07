package de.y2k.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Startanimationen im VFD-Punktraster (Pixelpuffer 146×110, wie der Vollbild-Visualizer).
 * kind 2 = Pixel-Regen (Logo regnet zusammen), 3 = Hyperraum (Sternenflug, Blitz, Logo zoomt heran).
 */
class Boot(private val kind: Int) {
    private val rng = Random(7)
    private val logo = "P-HR-X1"
    private val LS = 3
    private val lx = (146 - Font.width(logo, LS)) / 2
    private val ly = 30

    // Pixel-Regen: jedes Logo-Pixel ist ein eigener Tropfen
    private class Drop(val x: Int, val y: Int, val y0: Float, val start: Int)
    private val drops: List<Drop> by lazy {
        val out = ArrayList<Drop>(); var x = lx
        for (ch in logo) { val g = Font.glyph(ch)
            for (k in 0 until 7) for (j in 0 until 5) if (g[k][j]) for (a in 0 until LS) for (b in 0 until LS) {
                val px = x + j * LS + b; val py = ly + k * LS + a
                out += Drop(px, py, -2f - rng.nextFloat() * 25f, 900 + (px * 5) + (rng.nextFloat() * 520).toInt())
            }
            x += Font.adv(ch) * LS }
        out
    }

    // Hyperraum: Sterne im Raum
    private class Star(var x: Float, var y: Float, var z: Float)
    private val stars = List(130) { Star(rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1, .05f + rng.nextFloat() * .95f) }
    private var lastT = 0L

    fun draw(p: Pix, t: Long, vc: Int, v2: Int, info: String, sub: String) {
        p.clear(0)
        if (kind == 3) hyper(p, t, vc, v2) else rain(p, t, vc, v2)
        val tl = if (kind == 3) 2500L else 2300L
        if (t > tl) { val n = min(sub.length.toLong(), (t - tl) / 35).toInt(); val s = sub.take(n)
            p.text(s, (146 - Font.width(sub)) / 2, if (kind == 3) 68 else 62, 1, v2) }
        if (t > 2900) { p.text(info, (146 - Font.width(info)) / 2, if (kind == 3) 80 else 76, 1, vc, .75f)
            val cr = "VIBECODED WITH CLAUDE"; p.text(cr, (146 - Font.width(cr)) / 2, 98, 1, vc, .5f) }
    }

    private fun rain(p: Pix, t: Long, vc: Int, v2: Int) {
        val cy = 55
        // Einschalten: Leuchtlinie wächst aus der Mitte
        if (t < 380) { val w = (t / 380f * 73).toInt(); for (x in 73 - w..73 + w) { p.set(x, cy, v2); p.set(x, cy - 1, v2, .35f); p.set(x, cy + 1, v2, .35f) } }
        // Kalibrier-Sweep: Balken laufen einmal durch
        if (t in 300..1150) { val ph = (t - 300) / 850f
            for (i in 0 until 36) { val hgt = (70 * max(0f, 1 - abs(ph * 1.3f - .15f - i / 36f) * 5)).toInt()
                for (k in 0 until hgt) { if (k % 3 == 2) continue; for (x in 0 until 3) p.set(4 + i * 4 + x, 92 - k, Col.mix(vc, v2, k / 70f)) } } }
        // Tropfen fallen und setzen das Logo zusammen
        val glint = (t - 2350) * .16f - 20
        for (d in drops) {
            val u = (t - d.start) / 420f
            if (u <= 0f) continue
            if (u < 1f) { val y = d.y0 + (d.y - d.y0) * u * u
                p.set(d.x.toFloat(), y, Col.mix(v2, Col.WHITE, .3f))
                for (k in 1..3) p.set(d.x.toFloat(), y - k * 2, v2, .5f - k * .13f)
            } else {
                val fresh = t - d.start - 420 < 90
                val gl = if (t > 2350 && abs(d.x - d.y * .5f - glint) < 4) .55f else 0f
                p.set(d.x, d.y, if (fresh) Col.mix(v2, Col.WHITE, .4f) else Col.mix(vc, Col.WHITE, gl))
            }
        }
    }

    private fun hyper(p: Pix, t: Long, vc: Int, v2: Int) {
        val dt = (t - lastT).coerceIn(0, 100) / 33f; lastT = t
        val cx = 73f; val cy = 55f
        // Sternenflug, wird bis 1,5 s immer schneller, danach ruhig im Hintergrund
        val sp = if (t < 1500) .004f + (t / 1500f) * (t / 1500f) * .07f else .003f
        val dim = if (t < 1500) 1f else .35f
        for (s in stars) {
            val x0 = cx + s.x / s.z * 40; val y0 = cy + s.y / s.z * 40
            s.z -= sp * dt
            if (s.z <= .03f) { s.x = rng.nextFloat() * 2 - 1; s.y = rng.nextFloat() * 2 - 1; s.z = 1f; continue }
            val x1 = cx + s.x / s.z * 40; val y1 = cy + s.y / s.z * 40
            p.line(x0, y0, x1, y1, Col.mix(vc, v2, min(1f, sp * 18)), dim * min(1f, 1.2f - s.z))
        }
        // Lichtblitz beim Austritt
        if (t in 1500..2050) { val r = if (t < 1750) (t - 1500) / 250f * 95 else 95f; val al = if (t < 1750) 1f else 1 - (t - 1750) / 300f
            for (y in 0 until 110) for (x in 0 until 146) { val d = hypot(x - cx, (y - cy) * 1.3f); if (d < r) p.set(x, y, Col.mix(v2, Col.WHITE, .35f * (1 - d / 95)), al * (.35f + .65f * (1 - d / 95))) } }
        // Logo zoomt heran (ganzzahlige Pixelstufen 9 → 3)
        if (t > 1750) {
            val s = if (t < 2230) 3 + ((2230 - t) / 80).toInt() else 3
            val w = Font.width(logo, s); val x = (146 - w) / 2; val y = ly + 10 - 7 * s / 2   // Mitte bleibt stehen, bei s=3 genau ly
            p.text(logo, x, y, s, vc)
            if (t >= 2230) { val q = (t - 2230) / 500f   // Druckwelle nach dem Einrasten
                if (q < 1) { var an = 0f; while (an < 2 * PI) { p.set(cx + cos(an) * q * 90, ly + 10 + sin(an) * q * 30, v2, 1 - q); an += .015f } } }
        }
        // Oszilloskop-Linie unter dem Logo
        if (t > 2230) { val len = min(126f, (t - 2230) * .25f); var px = 10f; var py = 59f
            var x = 10f; while (x <= 10 + len) { val y = 59 + sin(x * .35f + t * .012f) * 2.5f * (1 - x / 146f); if (x > 10) p.line(px, py, x, y, v2, .8f); px = x; py = y; x += 1f } }
    }
}
