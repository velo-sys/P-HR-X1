package de.y2k.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Farbkontext: nur Haupt- und Zweitfarbe (plus helle/dunkle Abstufungen). */
class VC {
    var vc = 0; var v2 = 0; var pk = 0; var lvc = 0; var lv2 = 0; var bg = 0; var small = false
    fun g(t: Float) = Col.mix(vc, v2, t)
}

fun band(a: Audio, f: Float): Float { val x = clamp01(f) * 31; val i = x.toInt(); val r = x - i; return a.b[i] * (1 - r) + a.b[min(31, i + 1)] * r }
private fun rnd() = Random.nextFloat()
private const val TAU = (2 * PI).toFloat()

abstract class Vis {
    private var lb = -1
    protected var inited = false
    protected var t = 0f
    fun beatEdge(a: Audio): Boolean { val e = lb >= 0 && lb != a.beatN; lb = a.beatN; return e }
    abstract fun draw(p: Pix, a: Audio, c: VC)
}

object Viz {
    private val LIST: List<Pair<String, () -> Vis>> = listOf(
        "Balken" to { Bars(false) }, "Spiegel" to { Bars(true) }, "Punktmatrix" to { DotMatrix() }, "Oszilloskop" to { Scope() },
        "Wasserfall" to { Waterfall() }, "VU-Nadeln" to { VuNeedles() }, "LED-Pegel" to { LedMeter() }, "Pixel-Flammen" to { Flames() },
        "Warp" to { Warp() }, "Synth-Skyline" to { Skyline() }, "Lissajous" to { Lissajous() }, "Delfine" to { Dolphins() },
        "Plasma" to { Plasma() }, "Tunnel" to { Tunnel() }, "Galaxie" to { Galaxy() }, "Lava-Lampe" to { Lava() }, "Orbit" to { Orbit() },
        "Zellen" to { Cells() }, "Kaleidoskop" to { Kaleido() }, "DNA-Helix" to { Dna() }, "Pixel-Regen" to { Rain() },
        "Polyeder" to { Poly() }, "Quallen" to { Jelly() }, "Nachtfahrt" to { NightDrive() },
    )
    val NAMES = LIST.map { it.first }
    private val EN = mapOf("Balken" to "Bars", "Spiegel" to "Mirror", "Punktmatrix" to "Dot Matrix", "Oszilloskop" to "Oscilloscope",
        "Wasserfall" to "Waterfall", "VU-Nadeln" to "VU Needles", "LED-Pegel" to "LED Meter", "Pixel-Flammen" to "Pixel Flames",
        "Delfine" to "Dolphins", "Galaxie" to "Galaxy", "Lava-Lampe" to "Lava Lamp", "Zellen" to "Cells", "Kaleidoskop" to "Kaleidoscope",
        "DNA-Helix" to "DNA Helix", "Pixel-Regen" to "Pixel Rain", "Polyeder" to "Polyhedron", "Quallen" to "Jellyfish", "Nachtfahrt" to "Night Drive")
    fun label(i: Int) = NAMES[i.coerceIn(0, NAMES.size - 1)].let { if (Lang.en) EN[it] ?: it else it }
    fun labels() = NAMES.indices.map { label(it) }
    fun create(i: Int): Vis = LIST[i.coerceIn(0, LIST.size - 1)].second()
}

/* ---------------- klassische ---------------- */
class Bars(private val mirror: Boolean) : Vis() {
    private var pk = FloatArray(0)
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg)
        val n = if (c.small) 33 else 36; val bw = W / n; val off = (W - n * bw) / 2
        if (pk.size != n) pk = FloatArray(n)
        for (i in 0 until n) {
            val v = band(a, i / (n - 1f)); val x0 = off + i * bw
            if (!mirror) {
                val h = (v * (H - 1)).roundToInt()
                for (k in 0 until H) {
                    if (if (c.small) k % 2 == 1 else k % 3 == 2) continue
                    val on = k < h; val col = if (on) (if (k > (H - 1) * .8f) c.pk else c.g(k / (H - 1f))) else c.vc
                    for (x in x0 until x0 + bw - 1) p.set(x, H - 1 - k, col, if (on) 1f else .07f)
                }
                pk[i] = max(pk[i] - (if (c.small) .03f else .012f), v)
                val py = H - 1 - (pk[i] * (H - 1)).roundToInt(); for (x in x0 until x0 + bw - 1) p.set(x, py, c.pk)
            } else {
                val cy = (H - 1) / 2f; val h = (v * H / 2).roundToInt()
                for (k in 0..h) {
                    if (!c.small && k % 3 == 2) continue
                    val s = 1 - k / (H / 2f) * .35f; val col = if (k > H / 2f * .8f) c.pk else c.g(k / (H / 2f))
                    for (x in x0 until x0 + bw - 1) { p.set(x, floor(cy - k).toInt(), col, s); p.set(x, ceil(cy + k).toInt(), col, s) }
                }
            }
        }
    }
}

class DotMatrix : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg)
        var x = 0
        while (x < W) { val v = band(a, x / (W - 1f)); var y = H - 1
            while (y >= 0) { val l = (H - 1 - y) / (H - 1f); val on = l <= v; p.set(x, y, if (on) (if (l > .8f) c.pk else c.g(l)) else c.vc, if (on) 1f else .1f); y -= 2 }
            x += 2 }
    }
}

class Scope : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h
        if (!inited) { p.clear(c.bg); inited = true }
        p.fade(if (c.small) .3f else .55f, c.bg)
        if (!c.small) {
            for (x in 0 until W step 12) for (y in 0 until H step 2) p.set(x, y, c.vc, .1f)
            for (y in 7 until H step 12) for (x in 0 until W step 2) p.set(x, y, c.vc, .1f)
        }
        val cy = (H - 1) / 2f; var px0 = 0f; var py0 = 0f
        for (j in 0 until 128) { val x = j / 127f * (W - 1); val y = cy - a.w[j] * (H / 2f - 1); if (j > 0) p.line(px0, py0, x, y, c.g(j / 127f)); px0 = x; py0 = y }
    }
}

class Waterfall : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        if (!inited) { p.clear(c.bg); inited = true }
        p.shiftUp()
        for (x in 0 until p.w) { val v = band(a, x / (p.w - 1f)); p.set(x, p.h - 1, if (v > .85f) c.pk else c.g(min(1f, v * 1.2f)), if (v < .08f) .04f else v.pow(1.1f)) }
    }
}

class VuNeedles : Vis() {
    private val n = FloatArray(2)
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg)
        floatArrayOf(a.L, a.R).forEachIndexed { m, lv ->
            n[m] += (lv - n[m]) * .25f
            if (c.small) {
                val w = W / 2 - 4; val x0 = if (m == 1) W - w else 0
                for (k in 0 until w step 2) { val f = k / (w - 1f); p.set(x0 + k, H - 2, if (f > .78f) c.pk else c.g(f), .8f); if (k % 10 == 0) p.set(x0 + k, H - 3, c.vc, .6f) }
                val xn = x0 + n[m] * (w - 1)
                p.line(x0 + w / 2f + (xn - x0 - w / 2f) * .25f, H + 2f, xn, 0f, c.lv2)
                p.text(if (m == 1) "R" else "L", if (m == 1) x0 - 7 else x0 + w + 2, 4, 1, c.vc, .5f)
                return@forEachIndexed
            }
            val cx = (W * (if (m == 1) .75f else .25f)).roundToInt(); val py = H - 12; val R = 44f; val x0 = cx - 34; val x1 = cx + 34
            for (x in x0..x1) { p.set(x, 30, c.vc, .3f); p.set(x, H - 4, c.vc, .3f) }
            for (y in 30..H - 4) { p.set(x0, y, c.vc, .3f); p.set(x1, y, c.vc, .3f) }
            var d = -40f
            while (d <= 40f) { val r = d * PI.toFloat() / 180; p.set(cx + sin(r) * R, py - cos(r) * R, if (d > 18) c.pk else c.g((d + 40) / 58), .85f); d += .4f }
            for (dd in -40..40 step 10) { val r = dd * PI.toFloat() / 180
                p.line(cx + sin(r) * (R - 4), py - cos(r) * (R - 4), cx + sin(r) * (R - 1), py - cos(r) * (R - 1), if (dd > 18) c.pk else c.vc, .9f) }
            val na = (-40 + n[m] * 80) * PI.toFloat() / 180
            p.line(cx.toFloat(), py.toFloat(), cx + sin(na) * (R - 3), py - cos(na) * (R - 3), c.lv2)
            p.set(cx, py, c.pk); p.set(cx + 1, py, c.pk)
            p.text(if (m == 1) "R" else "L", x0 + 3, H - 12, 1, c.vc, .7f); p.text("VU", cx - 5, py - 20, 1, c.v2, .5f)
        }
    }
}

class LedMeter : Vis() {
    private val pk = FloatArray(2)
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; p.clear(c.bg)
        val x0 = if (c.small) 0 else 12; val segs = (W - x0) / 3
        floatArrayOf(a.L, a.R).forEachIndexed { ch, lv ->
            pk[ch] = max(pk[ch] - .008f, lv)
            val y0 = if (c.small) (if (ch == 1) 8 else 1) else (if (ch == 1) 64 else 30); val hh = if (c.small) 6 else 16
            val pkS = (pk[ch] * (segs - 1)).roundToInt()
            for (k in 0 until segs) { val f = k / (segs - 1f); val on = f <= lv || k == pkS; val col = if (f > .8f) c.pk else c.g(f / .8f)
                for (y in y0 until y0 + hh) { p.set(x0 + k * 3, y, col, if (on) 1f else .1f); p.set(x0 + k * 3 + 1, y, col, if (on) 1f else .1f) } }
            if (!c.small) p.text(if (ch == 1) "R" else "L", 2, y0 + 5, 1, c.vc, .8f)
        }
        if (!c.small) listOf("-30" to 0f, "-20" to .25f, "-10" to .5f, "-5" to .65f, "0" to .8f, "+3" to .95f).forEach { (s, f) ->
            p.text(s, (x0 + f * (segs - 1) * 3 - Font.width(s) / 2f).roundToInt(), 52, 1, if (f >= .8f) c.pk else c.g(f), .6f) }
    }
}

class Flames : Vis() {
    private var h = FloatArray(0)
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h
        if (h.size != W * (H + 2)) h = FloatArray(W * (H + 2))
        val dec = if (c.small) .05f else .012f
        for (x in 0 until W) { val v = band(a, x / (W - 1f)) * 1.25f; h[(H + 1) * W + x] = v * (.6f + .8f * rnd()); h[H * W + x] = v * (.6f + .8f * rnd()) }
        for (y in 0 until H) for (x in 0 until W) {
            val l = h[(y + 1) * W + max(0, x - 1)]; val m = h[(y + 1) * W + x]; val r = h[(y + 1) * W + min(W - 1, x + 1)]; val b = h[(y + 2) * W + x]
            h[y * W + x] = max(0f, (l + m + r + b) / 4.04f - dec)
        }
        for (y in 0 until H) for (x in 0 until W) { val v = h[y * W + x]
            when { v < .04f -> p.set(x, y, c.bg); v < .35f -> p.set(x, y, c.v2, v * 2.8f); v < .75f -> p.set(x, y, Col.mix(c.v2, c.vc, (v - .35f) / .4f)); else -> p.set(x, y, Col.mix(c.vc, c.lvc, min(1f, (v - .75f) * 3))) } }
    }
}

class Warp : Vis() {
    private class S(var x: Float, var y: Float, var z: Float)
    private val stars = List(90) { S(rnd() * 2 - 1, rnd() * 2 - 1, rnd()) }
    override fun draw(p: Pix, a: Audio, c: VC) {
        if (!inited) { p.clear(c.bg); inited = true }
        p.fade(.5f, c.bg)
        val bass = band(a, .02f); val sp = .008f + bass * .05f; val cx = p.w / 2f; val cy = p.h / 2f
        val kx = if (c.small) 60f else 26f; val ky = if (c.small) 5f else 26f
        stars.forEach { s ->
            val x0 = cx + s.x / s.z * kx; val y0 = cy + s.y / s.z * ky; s.z -= sp
            if (s.z <= .03f) { s.x = rnd() * 2 - 1; s.y = rnd() * 2 - 1; s.z = 1f; return@forEach }
            p.line(x0, y0, cx + s.x / s.z * kx, cy + s.y / s.z * ky, c.g(1 - s.z), min(1f, 1.2f - s.z))
        }
    }
}

/** Streifensonne am Horizont (cx, hz = Mittelpunkt unten) */
private fun sun(p: Pix, c: VC, cx: Float, hz: Int, r: Int, t: Float) {
    for (y in hz - r..hz) for (x in (cx - r).toInt()..(cx + r).toInt()) {
        if (hypot(x - cx, (y - hz).toFloat()) > r) continue
        val band = ((hz - y) + (t * 3).toInt()) % 5
        if (band == 0 && y > hz - r * .65f) continue
        p.set(x, y, Col.mix(c.v2, c.vc, (hz - y) / r.toFloat()), .95f)
    }
}
/** Bergkamm aus dem Spektrum – nur die Linie, keine Füllung (Sonne bleibt sichtbar) */
private fun ridge(p: Pix, a: Audio, c: VC, hz: Int, maxH: Float, sunR: Int = 0) {
    var py = hz.toFloat(); val cx = p.w / 2f
    for (x in 0 until p.w) { val v = band(a, abs(x - cx) / cx * .9f); val y = hz - (v * maxH + 1)
        val behindSun = sunR > 0 && abs(x - cx) <= sunR + 1   // Sonne bleibt immer frei
        if (x > 0 && !behindSun) p.line(x - 1f, py, x.toFloat(), y, c.vc, 1f); py = y }
}

class Skyline : Vis() {
    private var o = 0f
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg); t += .05f
        val bass = band(a, .02f); o += .03f + bass * .14f
        val cx = W / 2f
        if (c.small) {
            val hz = 9
            sun(p, c, cx, hz, 7, t)
            ridge(p, a, c, hz, 6f, 7)
            for (i in 0 until 4) { val z = (i + frac(o)) / 4; val y = hz + 1 + ((H - hz - 1) * z * z).roundToInt(); for (x in 0 until W) p.set(x, y, c.v2, .3f + .6f * z) }
            for (j in -12..12) p.line(cx + j * 3, hz + 1f, cx + j * 14, H - 1f, c.v2, .35f)
            return
        }
        val hz = (H * .55f).roundToInt()
        sun(p, c, cx, hz, 22, t)
        ridge(p, a, c, hz, 30f, 22)
        for (i in 0 until 12) { val z = (i + frac(o)) / 12; val y = hz + ((H - hz) * z * z).roundToInt(); for (x in 0 until W) p.set(x, y, c.v2, .25f + .6f * z) }
        for (j in -10..10) p.line(cx + j * 2, hz.toFloat(), cx + j * 18, H - 1f, c.v2, .45f)
    }
}

/** Nachtfahrt: Auto fährt in den Sonnenuntergang, Tempo = Energie der Musik, Sternschnuppen */
private val CAR = listOf(
    "........############........", ".......#wwwwwwwwwwww#.......", "......#wwwwwwwwwwwwww#......", ".....##################.....",
    "...######################...", "..########################..", ".#LLLLLL############LLLLLL#.", ".##########PPPPPP##########.",
    ".##########################.", "..TTTT................TTTT..", "..TTTT................TTTT..")
private val CAR_S = listOf("...######...", "..#wwwwww#..", ".##########.", "#LL######LL#", ".TT......TT.")
class NightDrive : Vis() {
    private class Sh(var x: Float, var y: Float, val vx: Float, val vy: Float, var l: Float)
    private var stars: List<Triple<Float, Float, Float>>? = null
    private val shoot = ArrayList<Sh>()
    private var off = 0f; private var spd = .03f
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; val sm = c.small; p.clear(c.bg); t += .05f
        val cx = W / 2f; val hz = if (sm) 6 else (H * .46f).roundToInt()
        val energy = (band(a, .05f) + band(a, .2f) + band(a, .45f) + band(a, .7f)) / 4
        spd += ((.012f + energy * .24f) - spd) * .12f; off += spd
        // Sterne + Sternschnuppen
        val st = stars ?: List(if (sm) 18 else 45) { Triple(rnd() * W, rnd() * (hz - 2), rnd() * 6f) }.also { stars = it }
        st.forEach { (x, y, ph) -> p.set(x, y, c.vc, .25f + .25f * sin(t * 2 + ph)) }
        val beat = beatEdge(a)
        if (rnd() < .012f || (beat && rnd() < .3f)) shoot += Sh(W * (.3f + rnd() * .7f), rnd() * hz * .5f, -(1.4f + rnd() * 2f) * (if (sm) 1.6f else 1f), (.5f + rnd() * .6f) * (if (sm) .3f else 1f), 1f)
        shoot.removeAll { it.l -= .035f; it.x += it.vx; it.y += it.vy; it.l <= 0 || it.x < -10 || it.y > hz }
        shoot.forEach { sh -> p.line(sh.x, sh.y, sh.x - sh.vx * 3, sh.y - sh.vy * 3, c.lv2, sh.l * .5f); p.set(sh.x, sh.y, c.lvc, sh.l) }
        // Sonne + Bergkamm
        sun(p, c, cx, hz, if (sm) 5 else 20, t)
        ridge(p, a, c, hz, if (sm) 3f else 12f, if (sm) 5 else 20)
        // Boden-Gitter neben der Straße und Straße
        val rows = if (sm) 3 else 10
        for (i in 0 until rows) { val z = (i + frac(off * 3)) / rows; val y = hz + 1 + ((H - hz - 1) * z * z).roundToInt()
            for (x in 0 until W) p.set(x, y, c.v2, .18f + .45f * z) }
        for (y in hz + 1 until H) {
            val tt = (y - hz) / (H - hz).toFloat(); val hw = 1.5f + tt * W * (if (sm) .16f else .42f)
            for (x in (cx - hw).toInt()..(cx + hw).toInt()) p.set(x, y, Col.mix(c.bg, c.vc, .05f))
            p.set(cx - hw, y.toFloat(), c.v2, .5f + .5f * tt); p.set(cx + hw, y.toFloat(), c.v2, .5f + .5f * tt)
            val zz = 1f / (tt + .02f)
            if (((zz * .9f - off * 6).toInt() and 1) == 0) { p.set(cx, y.toFloat(), c.lvc, .4f + .6f * tt); if (!sm && tt > .4f) p.set(cx + 1, y.toFloat(), c.lvc, .4f + .6f * tt) }
        }
        // Auto (federt im Beat, schwankt leicht mit den Mitten)
        val spr = if (sm) CAR_S else CAR
        val bounce = (a.kick * (if (sm) 1f else 2f)).roundToInt()
        val sx = (cx - spr[0].length / 2f + sin(t * .7f) * (if (sm) 1f else 3f) * band(a, .4f)).roundToInt()
        val sy = H - spr.size - (if (sm) 0 else 3) - bounce
        spr.forEachIndexed { r, row -> row.forEachIndexed { k, ch ->
            when (ch) {
                '#' -> p.set(sx + k, sy + r, Col.mix(c.vc, c.bg, .35f)); 'w' -> p.set(sx + k, sy + r, Col.mix(c.bg, c.vc, .2f))
                'L' -> p.set(sx + k, sy + r, c.lv2, .7f + .3f * a.kick); 'P' -> p.set(sx + k, sy + r, c.v2, .5f)
                'T' -> p.set(sx + k, sy + r, Col.mix(c.bg, c.vc, .12f))
            } } }
        if (!sm) { // Rücklicht-Schein auf der Straße
            for (k in 0 until 6) { p.set(sx + 2 + k, sy + spr.size + 1, c.v2, .25f * (1 - k / 6f)); p.set(sx + spr[0].length - 3 - k, sy + spr.size + 1, c.v2, .25f * (1 - k / 6f)) }
        }
    }
}

class Lissajous : Vis() {
    private var ph = 0f
    override fun draw(p: Pix, a: Audio, c: VC) {
        if (!inited) { p.clear(c.bg); inited = true }
        p.fade(.55f, c.bg); ph += .03f + band(a, .5f) * .05f
        val cx = p.w / 2f; val cy = p.h / 2f; val aL = .3f + a.L * .7f; val aR = .3f + a.R * .7f
        val jit = band(a, .9f) * (if (c.small) 1 else 4); val rx = p.w / 2f - (if (c.small) 4 else 6); val ry = p.h / 2f - (if (c.small) 1 else 6)
        var x0 = 0f; var y0 = 0f
        for (j in 0..240) { val th = j / 240f * TAU; val x = cx + sin(3 * th + ph) * aL * rx; val y = cy + sin(2 * th) * aR * ry + (rnd() - .5f) * jit
            if (j > 0) p.line(x0, y0, x, y, c.g(j / 240f)); x0 = x; y0 = y }
    }
}

/* ---------------- abgespacet ---------------- */
private val DOLPHIN = listOf("......##........", ".....####.......", "#..###########..", "###############.", "#..##########.##", "....#########...", "......##..##....")
private fun sprite(p: Pix, X: Int, Y: Int, slope: Float, c: VC, al: Float) {
    DOLPHIN.forEachIndexed { sy, row -> row.forEachIndexed { sx, ch ->
        if (ch == '#') p.set(X + sx, Y + sy + ((sx - 8) * slope).roundToInt(), if (sy >= 4) c.v2 else c.vc, al) } }
    if (al > .8f) p.set(X + 12, Y + 3 + (4 * slope).roundToInt(), c.bg)
}

class Dolphins : Vis() {
    private class D(var x: Float, var ph: Float = -1f, var h: Float = 0f, var splashed: Boolean = false)
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var l: Float)
    private var ds: List<D>? = null
    private val sp = ArrayList<P>(); private val bb = ArrayList<P>()
    private var stars: List<Pair<Float, Float>>? = null
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg); val small = c.small
        val hz = if (small) H - 5 else (H * .58f).roundToInt(); val depth = if (small) 3f else 28f
        t += .05f
        if (!small) {
            val st = stars ?: List(30) { rnd() * W to rnd() * hz * .8f }.also { stars = it }
            st.forEachIndexed { i, (x, y) -> p.set(x, y, if (i % 2 == 1) c.vc else c.v2, .3f + .3f * sin(t * 2 + i)) }
            val sx = W * .72f; val r = 24
            for (y in hz - r..hz) for (x in (sx - r).toInt()..(sx + r).toInt()) {
                if (hypot(x - sx, (y - hz).toFloat()) > r || ((hz - y) % 5 == 0 && y > hz - 16)) continue
                p.set(x, y, Col.mix(c.v2, c.vc, (hz - y) / r.toFloat()), .9f)
            }
        }
        val dl = ds ?: List(3) { D(it * W / 3f) }.also { ds = it }
        if (beatEdge(a)) dl.firstOrNull { it.ph < 0 }?.let { it.ph = 0f; it.splashed = false; it.h = (if (small) 4f else 20f) + band(a, .03f) * (if (small) 4f else 24f) }
        val air = ArrayList<Triple<Float, Float, Float>>(); val under = ArrayList<Triple<Float, Float, Float>>()
        dl.forEach { d ->
            d.x += if (small) 1.2f else .9f; if (d.x > W + 4) d.x = -18f
            if (d.ph < 0) return@forEach
            d.ph += .03f
            if (d.ph < 1) air += Triple(d.x, hz - 4 - sin(d.ph * PI.toFloat()) * d.h, -cos(d.ph * PI.toFloat()) * .45f)
            else {
                val q = (d.ph - 1) / .95f; val y = hz - 3 + sin(q * PI.toFloat()) * depth
                under += Triple(d.x, y, cos(q * PI.toFloat()) * .45f)
                if (!d.splashed) { d.splashed = true; repeat(9) { sp += P(d.x + 8, hz.toFloat(), (rnd() - .5f) * 1.6f, -rnd() * 2.2f, 1f) } }
                if (rnd() < .3f) bb += P(d.x + 4 + rnd() * 8, y, 0f, 0f, 1f)
                if (d.ph >= 1.95f) d.ph = -1f
            }
        }
        under.forEach { (x, y, s) -> sprite(p, x.roundToInt(), y.roundToInt(), s, c, .45f) }
        bb.removeAll { it.l -= .03f; it.y -= .5f; it.l <= 0 || it.y <= hz }
        bb.forEach { p.set(it.x + sin(it.y * .6f), it.y, c.lv2, it.l * .8f) }
        var y = hz
        while (y < H) { for (x in 0 until W) { val yy = y + (sin(x * .2f + t * 2 + y) * .8f).roundToInt()
                p.set(x, yy, c.g((y - hz) / (H - hz + 1f)), if (small) .6f else .3f + .45f * (y - hz) / (H - hz)) }
            y += if (small) 2 else 3 }
        air.forEach { (x, yy, s) -> sprite(p, x.roundToInt(), yy.roundToInt(), s, c, 1f) }
        sp.removeAll { it.l -= .06f; it.l <= 0 }
        sp.forEach { it.x += it.vx; it.y += it.vy; it.vy += .18f; p.set(it.x, it.y, c.lv2, it.l) }
    }
}

class Plasma : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        val bass = band(a, .03f); t += .04f + bass * .16f
        val cx = p.w / 2f; val cy = p.h / 2f; val e = .35f + .65f * min(1f, bass * 1.3f); val fy = if (c.small) 3f else 1f
        for (y in 0 until p.h) for (x in 0 until p.w) {
            val v = (sin(x * .16f + t) + sin(y * fy * .13f + t * 1.3f) + sin((x + y * fy) * .09f + t * .7f) + sin(hypot(x - cx, (y - cy) * fy) * .17f - t * 2) + 4) / 8
            val q = floor(v * 4 + dith(x, y) + .5f).coerceIn(0f, 4f) / 4
            p.set(x, y, c.g(q), (.25f + .75f * q) * e)
        }
    }
}

class Tunnel : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        t += .03f + band(a, .03f) * .12f
        val cx = p.w / 2f; val cy = p.h / 2f; val ky = if (c.small) 5f else 1f
        for (y in 0 until p.h) for (x in 0 until p.w) {
            val dx = x - cx; val dy = (y - cy) * ky; val r = hypot(dx, dy) + .01f; val u = 40 / r + t
            val an = atan2(dy, dx) / TAU * 12 + t * .15f
            val fog = min(1f, r / (if (c.small) 45f else 35f))
            if ((floor(u * 2).toInt() + floor(an).toInt()) and 1 == 1) p.set(x, y, c.g(frac(u)), fog) else p.set(x, y, c.vc, .05f * fog)
        }
    }
}

class Galaxy : Vis() {
    private class S(val r: Float, val a: Float)
    private val stars = List(280) { i -> val r = rnd().pow(.6f) * 50; S(r, (i % 2) * PI.toFloat() + r * .18f + rnd() * .5f) }
    private var rot = 0f
    override fun draw(p: Pix, a: Audio, c: VC) {
        if (!inited) { p.clear(c.bg); inited = true }
        p.fade(.4f, c.bg); rot += .02f + band(a, .4f) * .03f
        val cx = p.w / 2f; val cy = p.h / 2f; val kx = if (c.small) 1.3f else 1.35f; val ky = if (c.small) .12f else .7f
        stars.forEach { s -> val ang = s.a + rot * (1.5f / (1 + s.r * .05f)); p.set(cx + cos(ang) * s.r * kx, cy + sin(ang) * s.r * ky, c.g(s.r / 50), .55f + .6f * band(a, s.r / 50)) }
        val core = 1 + band(a, .02f) * (if (c.small) 2 else 5)
        var an = 0f
        while (an < TAU) { var r = 0f; while (r <= core) { p.set(cx + cos(an) * r * kx, cy + sin(an) * r * (if (c.small) .5f else ky), c.pk); r += .7f }; an += .3f }
    }
}

class Lava : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        p.clear(c.bg); t += .03f
        val cx = p.w / 2f; val cy = p.h / 2f; val sm = c.small
        val balls = List(5) { i -> floatArrayOf(cx + sin(t * (.5f + i * .13f) + i) * p.w * .36f, cy + cos(t * (.4f + i * .17f) + i * 2) * p.h * .34f, (if (sm) 3f else 8f) + band(a, i / 4f) * (if (sm) 4f else 12f)) }
        for (y in 0 until p.h) for (x in 0 until p.w) {
            var f = 0f
            for (b in balls) { val dx = x - b[0]; val dy = (y - b[1]) * (if (sm) 2.5f else 1f); f += b[2] * b[2] / (dx * dx + dy * dy + 1) }
            if (f < .8f) continue
            val fd = f + dith(x, y) * .4f
            p.set(x, y, c.g(if (fd > 2.2f) 1f else if (fd > 1.4f) .55f else .15f), if (f < 1) .5f else 1f)
        }
    }
}

class Orbit : Vis() {
    private var st: List<Pair<Float, Float>>? = null
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg); val sm = c.small; val bass = band(a, .03f); t += .02f + band(a, .4f) * .03f
        (st ?: List(if (sm) 12 else 40) { rnd() * W to rnd() * H }.also { st = it }).forEach { (x, y) -> p.set(x, y, c.vc, .25f) }
        val cx = W / 2f; val cy = H / 2f; val R = if (sm) 6 else 16; val rx = (if (sm) 16f else 30f) + bass * (if (sm) 3 else 5); val ry = if (sm) 2f else 7f
        fun ring(front: Boolean) { var an = 0f
            while (an < TAU) { val y = cy + sin(an) * ry
                if ((y > cy) == front) for (k in 0 until (if (sm) 1 else 3)) p.set(cx + cos(an) * (rx - k * 2), cy + sin(an) * (ry - k * .6f), c.g(k / 3f), if (front) 1f else .5f)
                an += .02f } }
        ring(false)
        for (y in -R..R) for (x in -R..R) { if (hypot(x.toFloat(), y.toFloat()) > R) continue
            val light = max(.15f, (-x * .5f - y * .6f) / R + .7f); val bnd = .5f + .5f * sin(y * .9f + t * 3)
            p.set(cx + x, cy + y, Col.mix(c.v2, c.vc, bnd), light) }
        ring(true)
        listOf(Triple(1.4f, 1.6f, .9f), Triple(1.8f, 2.2f, -.6f), Triple(2.2f, 2.8f, .4f)).forEachIndexed { i, (fx, fy, s) ->
            val an = t * s * 3 + i * 2; val x = cx + cos(an) * rx * fx * (if (sm) 1.6f else 1f); val y = cy + sin(an) * ry * fy * (if (sm) 1.5f else 1.6f)
            val col = if (i % 2 == 1) c.v2 else c.pk
            p.set(x, y, col); p.set(x + 1, y, col); if (!sm) { p.set(x, y + 1, c.pk); p.set(x + 1, y + 1, c.pk) }
        }
    }
}

class Cells : Vis() {
    private var g = ByteArray(0); private var f = 0; private var seeded = false
    override fun draw(p: Pix, a: Audio, c: VC) {
        val cs = 2; val gw = p.w / cs; val gh = max(1, p.h / cs)
        if (g.size != gw * gh) { g = ByteArray(gw * gh); seeded = false }
        if (beatEdge(a) || !seeded) { seeded = true
            repeat(if (c.small) 14 else 60) { val x = (rnd() * gw).toInt(); if (rnd() > band(a, x / gw.toFloat()) + .2f) return@repeat
                val y = (rnd() * gh).toInt(); for (dy in -1..1) for (dx in -1..1) if (rnd() < .5f) g[((y + dy + gh) % gh) * gw + (x + dx + gw) % gw] = 1 } }
        if (++f % 3 == 0) { val n = ByteArray(gw * gh)
            for (y in 0 until gh) for (x in 0 until gw) { var k = 0
                for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && g[((y + dy + gh) % gh) * gw + (x + dx + gw) % gw] > 0) k++
                val ag = g[y * gw + x].toInt()
                n[y * gw + x] = (if (ag > 0) (if (k == 2 || k == 3) min(120, ag + 1) else 0) else (if (k == 3) 1 else 0)).toByte() }
            g = n }
        p.fade(.5f, c.bg)
        for (y in 0 until gh) for (x in 0 until gw) { val ag = g[y * gw + x].toInt(); if (ag <= 0) continue
            val col = if (ag < 3) c.v2 else c.g(min(1f, ag / 12f))
            for (i in 0 until cs) for (j in 0 until cs) if (c.small || !(i > 0 && j > 0)) p.set(x * cs + j, y * cs + i, col) }
    }
}

class Kaleido : Vis() {
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg); t += .04f + band(a, .03f) * .08f
        val hw = ceil(W / 2f).toInt(); val hh = if (c.small) H else ceil(H / 2f).toInt()
        for (y in 0 until hh) for (x in 0 until hw) {
            val d1 = x / hw.toFloat(); val d2 = y / hh.toFloat(); val v = band(a, (d1 * d1 + d2 * d2) / 2)
            val pat = sin(x * .35f + t) * cos(y * (if (c.small) .9f else .3f) - t * 1.2f) + sin(x * y * .012f + t * .5f)
            if (pat + dith(x, y) * .3f < 1 - v * 1.7f) continue
            val col = c.g(frac(pat * .4f + t * .08f))
            p.set(x, y, col); p.set(W - 1 - x, y, col)
            if (!c.small) { p.set(x, H - 1 - y, col); p.set(W - 1 - x, H - 1 - y, col) }
        }
    }
}

class Dna : Vis() {
    private var bc = 0; private var tsp = .06f; private var spd = .06f
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg)
        if (beatEdge(a)) { bc++; if (bc % 8 == 0) tsp = floatArrayOf(-.06f, .015f, .05f, .09f, .15f, .22f)[(rnd() * 6).toInt().coerceAtMost(5)] }
        val tgt = tsp * (.45f + 1.3f * band(a, .4f)) + a.kick * .035f * sign(tsp)
        spd += (tgt - spd) * .06f; t += spd
        val cy = (H - 1) / 2f; val amp = (if (c.small) 6f else H * .3f) * (.65f + .35f * band(a, .4f))
        for (back in intArrayOf(1, 0)) for (x in 0 until W) {
            val ph = x * (if (c.small) .09f else .11f) + t; val y1 = cy + sin(ph) * amp; val y2 = cy + sin(ph + PI.toFloat()) * amp; val z = cos(ph)
            if (back == 1 && x % 7 == 0) { val v = band(a, x / W.toFloat()); val lo = min(y1, y2); val hi = max(y1, y2)
                var y = lo.roundToInt(); while (y <= hi) { val lit = abs(y - cy) < v * (hi - lo) / 2 + .5f; p.set(x, y, if (lit) c.pk else Col.mix(c.vc, c.v2, .5f), if (lit) 1f else .25f); y++ } }
            val frontStrand1 = (z > 0) == (back == 0)
            if (frontStrand1) { p.set(x.toFloat(), y1, c.vc, if (back == 1) .45f else 1f); if (!c.small && back == 0) p.set(x.toFloat(), y1 + 1, c.vc, .8f) }
            else { p.set(x.toFloat(), y2, c.v2, if (back == 1) .45f else 1f); if (!c.small && back == 0) p.set(x.toFloat(), y2 + 1, c.v2, .8f) }
        }
    }
}

class Rain : Vis() {
    private class Dr(val x: Int, var y: Float, val sp: Float)
    private val drops = ArrayList<Dr>()
    override fun draw(p: Pix, a: Audio, c: VC) {
        p.clear(c.bg); val cols = p.w / 2; val len = if (c.small) 5 else 16; val bass = band(a, .03f)
        for (i in 0 until cols) if (rnd() < band(a, i / cols.toFloat()) * .08f + .004f) drops += Dr(i * 2, -1f, .5f + rnd() * 1.2f)
        drops.removeAll { it.y += it.sp * (1 + bass * 1.5f); it.y - len >= p.h }
        drops.forEach { d -> for (k in 0 until len) { val y = d.y.roundToInt() - k; if (y < 0 || y >= p.h) continue
            p.set(d.x, y, if (k == 0) c.lv2 else c.g(k / len.toFloat()), if (k == 0) 1f else 1 - k / len.toFloat()) } }
    }
}

class Poly : Vis() {
    private var an = 0f
    private val V = arrayOf(floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f), floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f))
    private val E = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
    private val O = arrayOf(floatArrayOf(.78f, 0f, 0f), floatArrayOf(-.78f, 0f, 0f), floatArrayOf(0f, .78f, 0f), floatArrayOf(0f, -.78f, 0f), floatArrayOf(0f, 0f, .78f), floatArrayOf(0f, 0f, -.78f))
    private val OE = arrayOf(0 to 2, 0 to 3, 0 to 4, 0 to 5, 1 to 2, 1 to 3, 1 to 4, 1 to 5, 2 to 4, 2 to 5, 3 to 4, 3 to 5)
    override fun draw(p: Pix, a: Audio, c: VC) {
        p.clear(c.bg); an += .03f + band(a, .4f) * .05f
        fun one(cx: Float, cy: Float, s: Float, ang: Float) {
            fun pr(v: FloatArray): Pair<Float, Float> {
                val X = v[0] * cos(ang) - v[2] * sin(ang); var Z = v[0] * sin(ang) + v[2] * cos(ang)
                val Y = v[1] * cos(ang * .7f) - Z * sin(ang * .7f); Z = v[1] * sin(ang * .7f) + Z * cos(ang * .7f)
                val k = 3.5f / (Z + 3.5f); return (cx + X * s * k) to (cy + Y * s * k)
            }
            E.forEach { (i, j) -> val (x0, y0) = pr(V[i]); val (x1, y1) = pr(V[j]); p.line(x0, y0, x1, y1, c.vc) }
            OE.forEach { (i, j) -> val (x0, y0) = pr(O[i]); val (x1, y1) = pr(O[j]); p.line(x0, y0, x1, y1, c.v2) }
        }
        if (c.small) for (i in 0 until 6) one(p.w * (i + .5f) / 6, p.h / 2f, 4.2f * (1 + band(a, i / 5f) * .5f), an + i * .6f)
        else one(p.w / 2f, p.h / 2f, 24 * (1 + band(a, .03f) * .35f), an)
    }
}

class Jelly : Vis() {
    private class Pt(var x: Float, var y: Float)
    private class Q(var x: Float, var y: Float, val s: Float, val dir: Int = 1) { var tn: List<List<Pt>>? = null }
    private var full: List<Q>? = null
    private var side: List<Q>? = null
    override fun draw(p: Pix, a: Audio, c: VC) {
        val W = p.w; val H = p.h; p.clear(c.bg); t += .05f
        val pulse = a.kick; val hi = band(a, .85f)
        if (c.small) {
            val qs = side ?: listOf(Q(20f, 0f, 1f, 1), Q(W - 25f, 0f, 1f, -1), Q(W * .55f, 0f, 1f, 1), Q(W * .3f, 0f, 1f, -1)).also { side = it }
            qs.forEachIndexed { i, q ->
                q.x += q.dir * (.45f + pulse * 1.3f + i * .08f); if (q.x > W + 30) q.x = -30f; if (q.x < -30) q.x = W + 30f
                val y = H / 2f + sin(t * .8f + i * 2) * 1.5f; val ry = 6 * (1 + pulse * .12f); val rx = 4.5f * (1 - pulse * .3f); val d = q.dir
                for (k in 0 until 4) { var x0 = q.x; var y0 = y + (k - 1.5f) * 2.4f
                    for (j in 1..20) { val x1 = q.x - d * j * 1.15f
                        val y1 = y + (k - 1.5f) * (2.4f + pulse * j * .12f) + sin(t * 3 + j * .5f + k + i) * (.5f + band(a, k / 3f) * 1.4f) * j / 20
                        p.line(x0, y0, x1, y1, c.g(.4f + .6f * j / 20), 1 - j / 20f * .6f); x0 = x1; y0 = y1 }
                    p.set(x0, y0, c.lv2, .35f + hi * 1.2f) }
                var an = (-PI / 2).toFloat()
                while (an <= PI / 2) { var r = 0f
                    while (r <= 1f) { p.set(q.x + d * cos(an) * rx * r, y + sin(an) * ry * r, if (r > .8f) c.vc else c.g(r * .5f), if (r > .8f) 1f else .35f + .35f * pulse); r += .25f }
                    an += .05f }
                var yy = -ry; while (yy <= ry) { p.set(q.x, y + yy, c.lvc, .8f); yy += 1f }
            }
            return
        }
        val NT = 5; val NS = 16; val seg = 1.6f
        val qs = full ?: List(3) { Q((it + .5f) * W / 3, H * (.3f + it * .25f), .8f + it * .25f) }.also { full = it }
        qs.forEachIndexed { i, q ->
            q.y -= .25f + pulse * .9f; if (q.y < -24) { q.y = H + 26f; q.x = 15 + rnd() * (W - 30); q.tn = null }
            val rx = 11 * q.s * (1 - pulse * .22f); val ry = 8 * q.s * (1 + pulse * .18f)
            val tn = q.tn ?: List(NT) { List(NS) { Pt(q.x, q.y) } }.also { q.tn = it }
            tn.forEachIndexed { k, tt ->
                val amp = 1.4f * (.4f + band(a, k / (NT - 1f)) * 1.8f); val spread = (k - (NT - 1) / 2f) * pulse * .55f
                tt[0].x = q.x - rx * .75f + k * rx * .375f; tt[0].y = q.y + 1
                for (j in 1 until NS) { val pr = tt[j - 1]; val f = j / NS.toFloat()
                    val tx = pr.x + sin(t * 2.2f + j * .45f + k * 1.3f + i) * amp * f + spread * f * 2; val ty = pr.y + seg * q.s * (1 - pulse * .35f)
                    tt[j].x += (tx - tt[j].x) * .35f; tt[j].y += (ty - tt[j].y) * .35f }
                for (j in 1 until NS) p.line(tt[j - 1].x, tt[j - 1].y, tt[j].x, tt[j].y, c.g(.4f + .6f * j / NS), 1 - j / NS.toFloat() * .65f)
                p.set(tt[NS - 1].x, tt[NS - 1].y, c.lv2, .35f + hi * 1.2f)
            }
            var an = PI.toFloat()
            while (an <= TAU) { var r = 0f
                while (r <= 1f) { p.set(q.x + cos(an) * rx * r, q.y + sin(an) * ry * r, if (r > .85f) c.vc else c.g(r * .5f), if (r > .85f) 1f else .3f + .35f * pulse); r += .12f }
                an += .03f }
            var x = -rx; while (x <= rx) { p.set(q.x + x, q.y, c.lvc, .8f); x += 1f }
        }
    }
}
