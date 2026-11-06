package de.y2k.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.media.AudioManager
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import androidx.media3.common.util.UnstableApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Zeichnet die komplette Oberfläche (logisch 440x330, skaliert auf 960x720) und verarbeitet die Tasten. */
@UnstableApi
class DeckView(ctx: Context) : View(ctx), Choreographer.FrameCallback {
    private val o get() = Engine.opts
    private val LW = 440f; private val LH = 330f
    // feste Aufdruck-Farben (gehören zum Gehäuse, nicht zum Farbschema)
    private val PRINT_RED = 0xE0302A
    // Aufdruck – bewusst keine fremden Marken (kein Herstellername, kein Hi-Res-AUDIO-Logo)
    private val BRAND = "P-HR-X1"; private val BADGE = "Hi-Fi"

    // ---------- Zustand ----------
    private var mode = "boot"          // player | term | opt | win | viz | boot
    private var prev = "player"
    private var win = ""
    private var tcur = 0
    private var termMsg: String? = null
    private var lvl = "cat"; private var cat = 0; private var ocur = 0
    private var needRescan = false
    private var toastText = ""; private var toastUntil = 0L
    private var osdUntil = 0L
    private var bootStart = 0L
    private var lastInput = System.currentTimeMillis()
    private var keyLog = ArrayList<String>()
    private var running = false
    private var dmsg = ""; private var dmsgUntil = 0L
    private var seekDir = 0; private var seekShowUntil = 0L
    private var shownErr = 0L

    // Übergänge 0..1
    private var termP = 0f; private var optP = 0f; private var winP = 0f; private var vizP = 0f
    private var optScroll = 0f
    private val anim = HashMap<String, Float>()

    // Pixel-Grafik
    private val drum = Drum()
    private val audio = Audio()
    private val pixS = Pix(132, 15); private val pixF = Pix(147, 110)
    private var vizS: Vis? = null; private var vizSi = -1
    private var vizF: Vis? = null; private var vizFi = -1
    private val vcx = VC()
    private var lastLogic = 0L
    private var mqClock = 0f; private var lastFrame = 0L

    // Farben (pro Frame berechnet) – nur Haupt- und Zweitfarbe
    private var vc = 0; private var v2 = 0; private var glowC = 0

    // ---------- Zeichenwerkzeuge ----------
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val sansBI = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    private val sansB = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint().apply { isFilterBitmap = false; isDither = false }
    private val aluBmp: Bitmap = makeAlu()
    private val dotPaint = Paint().apply { shader = BitmapShader(makeDots(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); isFilterBitmap = false }
    private val scanPaint = Paint().apply { shader = BitmapShader(makeScan(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); isFilterBitmap = false }
    private var sc = 0f; private var ox = 0f; private var oy = 0f   // Skalierung + Rand für 4:3
    private val r = RectF(); private val path = Path(); private val dotMatrix = android.graphics.Matrix()

    init {
        isFocusable = true
        bootStart = System.currentTimeMillis()
        if (o["boot"] == 1) mode = "player"
        Engine.onChange = { postInvalidate() }
    }

    fun onShow() { if (!running) { running = true; Choreographer.getInstance().postFrameCallback(this) } }
    fun onHide() { running = false }

    private var lastInv = 0L
    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (frameTimeNanos - lastInv >= 30_000_000L) { lastInv = frameTimeNanos; invalidate() }   // max. ~30 Bilder/s (Akku)
        Choreographer.getInstance().postFrameCallback(this)
    }

    // ================= Logik-Takt (20/s) =================
    private fun logic(now: Long) {
        val n = Engine.folders.size
        Engine.analyzer.pull(audio, Engine.playing(), o["vizsm"])
        // Intensität folgt der Lautstärke: ab 50 % volle Wucht
        if (now - volT > 400) { volT = now; val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            volFrac = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toFloat() }
        val inten = if (o["vizvol"] == 1) (volFrac / .5f).coerceIn(.06f, 1f) else 1f
        for (i in 0 until 32) va.b[i] = audio.b[i] * inten
        for (j in 0 until 128) va.w[j] = audio.w[j] * inten
        va.L = audio.L * inten; va.R = audio.R * inten; va.kick = audio.kick * inten; va.beatN = audio.beatN
        drum.mode = o["drum"]; drum.step()
        if (!drumSynced && n > 0) { drum.jump(Engine.cur); drumSynced = true }
        if (vizSi != o["viz"]) { vizSi = o["viz"]; vizS = Viz.create(vizSi) }   // klein und groß immer synchron
        if (vizFi != o["viz"]) { vizFi = o["viz"]; vizF = Viz.create(vizFi) }
        fillVc(true)
        if (mode == "viz" || vizP > 0.01f || (mode == "opt" && curItem()?.k == "viz")) { vcx.bg = 0; vcx.small = false; vizF?.draw(pixF, va, vcx); pixF.flush() }
        if (mode != "viz") { vcx.bg = 0x05080A; vcx.small = true; vizS?.draw(pixS, va, vcx); pixS.flush() }
        // Bildschirm an halten
        val keep = intArrayOf(0, 30_000, 60_000, 300_000)[o["off"].coerceIn(0, 3)]
        (context as? MainActivity)?.keepScreenOn(keep == 0 || now - lastInput < keep)
    }
    private var drumSynced = false
    private val va = Audio(); private var volFrac = 1f; private var volT = 0L

    private fun fillVc(forViz: Boolean) {
        val sw = forViz && o["col2m"] == 1 && (audio.beatN and 1) == 1
        val a = if (sw) v2 else vc; val b = if (sw) vc else v2
        vcx.vc = a; vcx.v2 = b; vcx.lvc = Col.mix(a, Col.WHITE, .45f); vcx.lv2 = Col.mix(b, Col.WHITE, .4f)
        vcx.pk = when (o["peak"]) { 0 -> b; 2 -> vcx.lvc; else -> vcx.lv2 }
    }

    private fun computeColors(now: Long) {
        val ci = o["color"].coerceIn(0, Palette.COLORS.size - 1)
        val base = Palette.COLORS[ci]
        val c2i = o["col2"]
        var a = base.c; var b = if (c2i >= Palette.RAINBOW) base.c else Palette.COLORS[c2i.coerceIn(0, Palette.RAINBOW - 1)].c
        glowC = base.glow
        if (ci == Palette.RAINBOW) { val h = (now % 9000L) / 9000f * 360f; a = Col.hue(a, h); b = Col.hue(b, h); glowC = Col.hue(glowC, h) }
        var br = floatArrayOf(1f, .8f, .6f, .4f)[o["bright"].coerceIn(0, 3)]
        if (o["flick"] == 1 && Random.nextFloat() < .04f) br *= .8f
        vc = Col.scale(a, br); v2 = Col.scale(b, br); glowC = Col.scale(glowC, br)
    }

    // ================= Zeichnen =================
    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) 0f else min(.1f, (now - lastFrame) / 1000f); lastFrame = now
        if (Engine.playing()) mqClock += dt
        computeColors(now)
        if (now - lastLogic >= 50) { lastLogic = now; logic(now) }
        if (mode == "boot" && now - bootStart > 3600) mode = "player"
        fun ease(v: Float, on: Boolean, s: Float = 2.8f) = if (on) min(1f, v + dt * s) else max(0f, v - dt * s)
        termP = ease(termP, mode == "term" || (mode == "win" && prev == "term"))
        optP = ease(optP, mode == "opt" || (mode == "win" && prev == "opt"))
        winP = ease(winP, mode == "win", 5f)
        vizP = ease(vizP, mode == "viz", 4f)

        // festes Seitenverhältnis 4:3 – nie breit/hoch ziehen, Rest schwarz (Letterbox)
        sc = min(width / LW, height / LH); ox = (width - LW * sc) / 2f; oy = (height - LH * sc) / 2f
        if (ox > .5f || oy > .5f) c.drawColor(0xFF000000.toInt())
        c.save(); c.translate(ox, oy); c.scale(sc, sc); c.clipRect(0f, 0f, LW, LH)
        drawBitmapFull(c, aluBmp)
        val tr = o["trans"]
        val e = { v: Float -> 1 - (1 - v) * (1 - v) * (1 - v) }
        // Hauptbild
        c.save()
        if (tr == 0) c.translate(-LW * e(optP) + LW * e(termP), 0f)
        drawPlayer(c, now)
        c.restore()
        val dim = if (tr == 0) optP * .45f else max(optP, 0f) * .6f
        if (dim > 0) { fp.shader = null; fp.color = Col.argb(0, (dim * 255).toInt()); c.drawRect(0f, 0f, LW, LH, fp) }
        // Bash-Ordnerwahl
        if (termP > 0) layer(c, tr, termP, fromLeft = true) { drawTerm(it) }
        if (mode == "browse") drawBrowse(c)
        // Optionsleiste
        if (optP > 0) layer(c, tr, optP, fromLeft = false) { drawOpt(it, dt) }
        // Fenster
        if (winP > 0) { fp.shader = null; fp.color = Col.argb(0, (winP * 170).toInt()); c.drawRect(0f, 0f, LW, LH, fp); c.save(); c.saveLayerAlpha(0f, 0f, LW, LH, (winP * 255).toInt()); drawWin(c, now); c.restore(); c.restore() }
        // Vollbild-Visualizer
        if (vizP > 0) { c.saveLayerAlpha(0f, 0f, LW, LH, (vizP * 255).toInt()); drawViz(c); c.restore() }
        if (mode == "boot") drawBoot(c, now)
        if (now < osdUntil) drawOsd(c)
        if (Engine.errorAt > shownErr) { shownErr = Engine.errorAt; toastText = Engine.errorMsg; toastUntil = now + 2500 }
        if (now < toastUntil) drawToast(c)
        c.restore()
    }

    private inline fun layer(c: Canvas, tr: Int, p: Float, fromLeft: Boolean, x0: Float = 0f, draw: (Canvas) -> Unit) {
        val e = 1 - (1 - p) * (1 - p) * (1 - p)
        c.save()
        when (tr) {
            0 -> c.translate(if (fromLeft) -LW * (1 - e) else (LW - x0) * (1 - e), 0f)
            1 -> c.saveLayerAlpha(0f, 0f, LW, LH, (p * 255).toInt())
            else -> c.clipRect(0f, 0f, LW, LH * e)
        }
        draw(c)
        if (tr == 1) c.restore()
        c.restore()
    }

    private fun drawBitmapFull(c: Canvas, b: Bitmap) { r.set(0f, 0f, LW, LH); c.drawBitmap(b, null, r, null) }

    // ---------- Text-Helfer ----------
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, face: Typeface = mono, glow: Boolean = true, align: Paint.Align = Paint.Align.LEFT, alpha: Int = 255) {
        tp.typeface = face; tp.textSize = size; tp.color = Col.argb(col, alpha); tp.textAlign = align
        val gr = floatArrayOf(5f, 2.5f, 0f)[o["glow"].coerceIn(0, 2)]
        if (glow && gr > 0) tp.setShadowLayer(gr * size / 22f + 1f, 0f, 0f, Col.argb(glowC, alpha)) else tp.clearShadowLayer()
        c.drawText(s, x, y, tp)
    }
    private fun tw(s: String, size: Float, face: Typeface = mono): Float { tp.typeface = face; tp.textSize = size; return tp.measureText(s) }

    private fun vfdBox(c: Canvas, l: Float, t: Float, rr: Float, b: Float, round: Float = 5f) {
        fp.shader = null; fp.color = Col.argb(0xFFFFFF, 0x99); r.set(l, t + 1, rr, b + 1); c.drawRoundRect(r, round, round, fp)
        fp.color = Col.argb(0x05080A); r.set(l, t, rr, b); c.drawRoundRect(r, round, round, fp)
    }
    private fun vfdOverlay(c: Canvas, l: Float, t: Float, rr: Float, b: Float, light: Boolean = false) {
        dotPaint.alpha = if (light) 95 else 255
        c.drawRect(l, t, rr, b, dotPaint)
        if (o["scanl"] == 1) c.drawRect(l, t, rr, b, scanPaint)
    }

    // ---------- Hauptbild ----------
    private fun icon(c: Canvas, ic: Array<String>, x: Float, y: Float, px: Float, col: Int, on: Boolean) {
        fp.shader = null; fp.color = Col.argb(col, if (on) 255 else 46)
        if (on && o["glow"] < 2) fp.setShadowLayer(3f, 0f, 0f, Col.argb(glowC)) else fp.clearShadowLayer()
        ic.forEachIndexed { r0, row -> row.forEachIndexed { c0, ch -> if (ch == '#') c.drawRect(x + c0 * px, y + r0 * px, x + (c0 + 1) * px - .25f, y + (r0 + 1) * px - .25f, fp) } }
        fp.clearShadowLayer()
    }

    private fun drawPlayer(c: Canvas, now: Long) {
        // Aufdruck: App-Name mit festem rotem Block dahinter
        text(c, BRAND, 12f, 17f, 15f, 0x15181B, sansBI, glow = false)
        val kw = tw(BRAND, 15f, sansBI)
        path.reset(); path.moveTo(12 + kw + 7, 18f); path.lineTo(12 + kw + 11, 5f); path.lineTo(12 + kw + 18, 5f); path.lineTo(12 + kw + 14, 18f); path.close()
        fp.shader = null; fp.color = Col.argb(PRINT_RED); c.drawPath(path, fp)
        val dw = tw("Digital Audio", 10f, sansBI); val hw = tw(BADGE, 10f, sansBI)
        fp.shader = LinearGradient(0f, 6f, 0f, 17f, intArrayOf(0xFFF3D27A.toInt(), 0xFFD4A62A.toInt(), 0xFFB8861B.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(428 - dw - hw - 16, 6f, 428 - dw - 4, 17f, fp); fp.shader = null
        text(c, BADGE, 428 - dw - hw - 10, 15f, 10f, 0x05080A, sansBI, glow = false)
        text(c, "Digital Audio", 428 - dw, 15f, 10f, 0x15181B, sansBI, glow = false)

        // VFD
        vfdBox(c, 10f, 25f, 430f, 213f)
        c.save(); c.clipRect(12f, 27f, 428f, 211f)
        var x = 22f
        fun badge(s: String, filled: Boolean, on: Boolean = true) {
            val w = tw(s, 11f) + 8
            fp.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE; fp.strokeWidth = 1f; fp.color = Col.argb(vc, if (on) 255 else 46)
            r.set(x, 32f, x + w, 46f); c.drawRect(r, fp); fp.style = Paint.Style.FILL
            text(c, s, x + 4, 43f, 11f, if (filled) 0x05080A else vc, glow = !filled, alpha = if (on) 255 else 46)
            x += w + 6
        }
        val f = Engine.fmt
        if (o["fmt"] == 1 && f.first.isNotEmpty()) { badge(f.first, true); if (f.second.isNotEmpty()) badge(f.second, false); if (f.third.isNotEmpty()) badge(f.third, false); badge("HI-RES", false, Engine.hires) }
        // Symbole rechts: Schlaf-Timer, Wiederholen, Zufall
        val sl = Engine.sleepLeftMin()
        var ix = 418f
        if (sl > 0) { val t = "$sl"; text(c, t, ix, 44f, 11f, vc, align = Paint.Align.RIGHT); ix -= tw(t, 11f) + 3 }
        ix -= 7 * 1.6f; icon(c, Icons.MOON, ix, 34f, 1.6f, vc, sl > 0); ix -= 10f
        if (o["rep"] == 1) { text(c, "1", ix, 44f, 11f, vc, align = Paint.Align.RIGHT); ix -= tw("1", 11f) + 2 }
        ix -= 9 * 1.6f; icon(c, Icons.REPEAT, ix, 34f, 1.6f, vc, o["rep"] > 0); ix -= 10f
        ix -= 9 * 1.6f; icon(c, Icons.SHUFFLE, ix, 34f, 1.6f, vc, o["shuf"] == 1)

        val fo = Engine.folders.getOrNull(Engine.cur)
        val tn = fo?.tracks?.size ?: 0
        text(c, "F%02d".format(Engine.cur + 1), 22f, 70f, 22f, vc)
        if (now < dmsgUntil) text(c, fit(dmsg, 250f, 18f), 220f, 69f, 18f, v2, align = Paint.Align.CENTER)
        else text(c, "T%02d/%02d".format(Engine.index() + 1, tn), 220f, 70f, 22f, vc, align = Paint.Align.CENTER)
        val tStr = fmtTime(Engine.pos()); val tW = tw(tStr, 22f)
        text(c, tStr, 418f, 70f, 22f, v2, align = Paint.Align.RIGHT)
        val st = when { now < seekShowUntil -> if (seekDir < 0) Icons.REW else Icons.FF; Engine.playing() -> Icons.PLAY; else -> Icons.PAUSE }
        icon(c, st, 418f - tW - 8 - 9 * 2f, 54f, 2f, v2, true)

        // Laufschrift
        c.save(); c.clipRect(22f, 74f, 418f, 130f)
        drawMarquee(c, marqueeText(), 22f, 118f, 396f)
        c.restore()
        // kleiner Visualizer
        r.set(22f, 140f, 418f, 185f); c.drawBitmap(pixS.bmp, null, r, bp)
        c.restore()
        vfdOverlay(c, 10f, 25f, 430f, 213f)

        // Walze
        drum.draw(Engine.folders, vc, v2)
        vfdBox(c, 10f, 220f, 430f, 322f)
        r.set(10f, 220f, 430f, 322f); c.drawBitmap(drum.pix.bmp, null, r, bp)
        // gleiches VFD-Punktraster wie oben, exakt auf die Walzen-Pixel (3 logische px) ausgerichtet
        dotMatrix.setTranslate(10f, 220f); dotPaint.shader.setLocalMatrix(dotMatrix); dotPaint.alpha = 255
        c.drawRect(10f, 220f, 430f, 322f, dotPaint)
        dotMatrix.reset(); dotPaint.shader.setLocalMatrix(dotMatrix)
        fp.shader = LinearGradient(10f, 220f, 160f, 400f, intArrayOf(0, 0x1EFFFFFF, 0, 0, 0x12FFFFFF, 0), floatArrayOf(.28f, .36f, .44f, .68f, .72f, .76f), Shader.TileMode.CLAMP)
        c.drawRect(10f, 220f, 430f, 322f, fp); fp.shader = null
        if (o["scanl"] == 1) c.drawRect(10f, 220f, 430f, 322f, scanPaint)
        fp.color = Col.argb(v2)
        path.reset(); path.moveTo(10f, 260f); path.lineTo(21f, 271f); path.lineTo(10f, 282f); path.close(); c.drawPath(path, fp)
        path.reset(); path.moveTo(430f, 260f); path.lineTo(419f, 271f); path.lineTo(430f, 282f); path.close(); c.drawPath(path, fp)
    }

    private fun marqueeText(): String {
        val t = Engine.track() ?: return if (Engine.scanning) L("SUCHE MUSIK …", "SEARCHING MUSIC …") else L("KEINE MUSIK GEFUNDEN · START › BIBLIOTHEK › NEU EINLESEN", "NO MUSIC FOUND · START › LIBRARY › RESCAN")
        val fo = Engine.folders.getOrNull(Engine.cur)
        var s = if (t.artist.isBlank()) t.title else "${t.artist} — ${t.title}"
        if (o["mtext"] == 1 && t.album.isNotBlank()) s += "  ◆  ALBUM: ${t.album}"
        if (o["mtext"] == 2 && fo != null) s += "  ◆  " + L("ORDNER", "FOLDER") + " %02d ${fo.name}".format(Engine.cur + 1)
        return s.uppercase()
    }

    private fun drawMarquee(c: Canvas, s: String, x0: Float, base: Float, w: Float) {
        val size = 50f; val sp = floatArrayOf(1.6f, 1f, .6f)[o["mspeed"].coerceIn(0, 2)]
        val t = mqClock
        when (o["mstyle"]) {
            1 -> { val tw0 = tw(s, size); val d = max(0f, tw0 - w + 4)
                if (d <= 0) { text(c, s, x0, base, size, vc); return }
                val per = 1.5f + d / 90f * sp; var ph = (t % (2 * per)) / per; if (ph > 1) ph = 2 - ph
                val q = ((ph - .12f) / .76f).coerceIn(0f, 1f); val e = q * q * (3 - 2 * q)
                text(c, s, x0 - d * e, base, size, vc) }
            2 -> { val per = 13; val cps = 1f / (.085f * sp); val pages = (s.length + per - 1) / per
                val pageDur = per / cps + 1.0f; val total = pageDur * pages; val tt = t % total; val pg = (tt / pageDur).toInt()
                val into = tt - pg * pageDur; val n = min(per, (into * cps).toInt()); val part = s.drop(pg * per).take(n)
                text(c, part, x0, base, size, vc)
                if ((System.currentTimeMillis() / 500) % 2 == 0L) { fp.color = Col.argb(vc); val cx = x0 + tw(part, size) + 3; c.drawRect(cx, base - 36, cx + 22, base + 4, fp) } }
            else -> { val full = "$s  ◆  "; val fw = tw(full, size); val per = (full.length * .36f * sp); val off = (t / per * fw) % fw
                if (o["mstyle"] == 3) {
                    tp.typeface = mono; tp.textSize = size
                    val ws = FloatArray(full.length); tp.getTextWidths(full, ws)
                    var xx = x0 - off; var k = 0
                    while (xx < x0 + w) { val i = k % full.length; val ch = full[i]
                        val lift = 7f * (sin((t * 2 * PI / 1.4 + k * .4).toFloat()) + 1f) / 2f
                        if (xx > x0 - ws[i] - 1) text(c, ch.toString(), xx, base - lift, size, vc); xx += ws[i]; k++ }
                } else { var xx = x0 - off; while (xx < x0 + w) { text(c, full, xx, base, size, vc); xx += fw } } }
        }
    }

    // ---------- Terminal-Bildschirme (große Schrift) ----------
    private class TL(val t: String, val sel: Boolean = false, val dim: Boolean = false, val col: Int? = null)
    private val TFS = 20f; private val TLH = 25f
    private fun termScreen(c: Canvas, lines: List<TL>, prompt: String, hint: String) {
        c.drawBitmap(aluBmp, null, RectF(0f, 0f, LW, LH), null)
        vfdBox(c, 8f, 8f, 432f, 322f)
        c.save(); c.clipRect(10f, 10f, 430f, 320f)
        var y = 34f
        lines.forEach { l ->
            if (l.sel) { fp.color = Col.argb(vc); c.drawRect(16f, y - 19, 424f, y + 6, fp); text(c, l.t, 20f, y, TFS, 0x05080A, glow = false) }
            else text(c, l.t, 20f, y, TFS, l.col ?: vc, alpha = if (l.dim) 130 else 255)
            y += TLH
        }
        text(c, prompt, 20f, 284f, TFS, vc)
        if ((System.currentTimeMillis() / 500) % 2 == 0L) { fp.color = Col.argb(vc); val cx = 20 + tw(prompt, TFS) + 2; c.drawRect(cx, 267f, cx + 11, 287f, fp) }
        text(c, hint, 20f, 310f, 13f, v2)
        c.restore()
        vfdOverlay(c, 8f, 8f, 432f, 322f, light = true)
    }
    private fun window(list: List<TL>, cur: Int, rows: Int): List<TL> {
        val start = (cur - rows / 2).coerceIn(0, max(0, list.size - rows)); return list.subList(start, min(list.size, start + rows))
    }

    private fun drawTerm(c: Canvas) {
        val fs = Engine.folders
        val all = fs.mapIndexed { i, f -> TL("%3dT  %s/%s".format(f.tracks.size, f.name, if (i == Engine.cur) "  ♫" else ""), sel = i == tcur) }
        val lines = ArrayList<TL>(); lines += TL("p-hr-x1:/Music$ ls", dim = true)
        lines += window(all, tcur, 8)
        lines += TL("${fs.size} " + L("Ordner", "folders") + " · ${fs.sumOf { it.tracks.size }} " + L("Lieder", "tracks"), dim = true)
        termScreen(c, lines, termMsg ?: "$ cd \"${fs.getOrNull(tcur)?.name ?: ""}\"", L("▲▼ WÄHLEN   A/▶ ABSPIELEN   B ZURÜCK", "▲▼ SELECT   A/▶ PLAY   B BACK"))
    }

    // ---------- Ordner hinzufügen (Bash-Browser über alle Speicher) ----------
    private val bStack = ArrayList<Dir>(); private var bcur = 0; private var bChanged = false
    private fun bEntries(): List<Dir?> { val d = bStack.lastOrNull() ?: Engine.tree; return (if (bStack.size > 0) listOf<Dir?>(null) else emptyList()) + d.kids.values }
    private fun bPath() = "/" + bStack.joinToString("/") { it.name }
    private fun drawBrowse(c: Canvas) {
        val added = o.added(); val es = bEntries()
        val all = es.mapIndexed { i, d -> if (d == null) TL("       ../", sel = i == bcur)
            else TL("%4dT %s %s/".format(d.count, if (d.key in added) "[+]" else "[ ]", d.name), sel = i == bcur, col = if (d.key in added) v2 else null) }
        val lines = ArrayList<TL>(); lines += TL("p-hr-x1:${bPath()}$ ls", dim = true)
        lines += window(all, bcur, 9)
        val d = es.getOrNull(bcur)
        termScreen(c, lines, if (d == null) "$ cd .." else "$ ${if (d.key in added) "rm" else "add"} \"${d.name}\"", L("▶ ÖFFNEN  ◀ ZURÜCK  A HINZU/WEG  B FERTIG", "▶ OPEN  ◀ UP  A ADD/REMOVE  B DONE"))
    }
    private fun browseKey(k: String) {
        val es = bEntries(); val d = es.getOrNull(bcur)
        when (k) {
            "up" -> bcur = (bcur - 1 + es.size) % max(1, es.size)
            "down" -> bcur = (bcur + 1) % max(1, es.size)
            "right", "x" -> if (d != null && d.kids.isNotEmpty()) { bStack += d; bcur = 0 } else if (d == null) bUp()
            "left" -> bUp()
            "a" -> if (d == null) bUp() else { val on = o.toggleAdded(d.key); bChanged = true; toast(if (on) "+ ${d.name.uppercase()}" else "- ${d.name.uppercase()}") }
            "b", "start" -> { mode = "opt"; if (bChanged) { bChanged = false; needRescan = true } }
        }
    }
    private fun bUp() { if (bStack.isNotEmpty()) { val last = bStack.removeAt(bStack.size - 1); bcur = bEntries().indexOf(last).coerceAtLeast(0) } }

    // ---------- Optionen: eigenes Vollbild-Fenster mit eigenem Display ----------
    private val OPW = 440f
    private fun curItem(): Item? = if (lvl == "sub") Menu.CATS[cat].items.getOrNull(ocur) else null
    private fun drawOpt(c: Canvas, dt: Float) {
        c.drawBitmap(aluBmp, null, RectF(0f, 0f, LW, LH), null)
        val cats = Menu.CATS
        // Aufdruck
        text(c, BRAND, 12f, 17f, 15f, 0x15181B, sansBI, glow = false)
        val kw = tw(BRAND, 15f, sansBI)
        path.reset(); path.moveTo(12 + kw + 7, 18f); path.lineTo(12 + kw + 11, 5f); path.lineTo(12 + kw + 18, 5f); path.lineTo(12 + kw + 14, 18f); path.close()
        fp.shader = null; fp.color = Col.argb(PRINT_RED); c.drawPath(path, fp)
        text(c, "SETUP", 12 + kw + 22, 17f, 15f, 0x15181B, sansBI, glow = false)
        text(c, if (lvl == "cat") L("START = SCHLIESSEN", "START = CLOSE") else L("B = ZURÜCK · START = SCHLIESSEN", "B = BACK · START = CLOSE"), 428f, 16f, 10f, 0x15181B, sansBI, glow = false, align = Paint.Align.RIGHT)
        // ----- eigenes Display -----
        val dl = 10f; val dt0 = 24f; val dr = 430f; val db = 122f
        vfdBox(c, dl, dt0, dr, db)
        c.save(); c.clipRect(dl + 2, dt0 + 2, dr - 2, db - 2)
        val it = curItem()
        text(c, if (lvl == "cat") L("OPTIONEN", "OPTIONS") else L("OPTIONEN › ", "OPTIONS › ") + cats[cat].name.uppercase(), 22f, 42f, 12f, vc, alpha = 170)
        when {
            it == null -> {
                val ct = cats[ocur]
                text(c, ct.icon + " " + ct.name.uppercase(), 22f, 80f, 30f, vc)
                text(c, fit(ct.items.joinToString(" · ") { it.label }, 396f, 12f), 22f, 106f, 12f, v2)
            }
            it.k == "viz" -> {
                text(c, fit(it.values[o[it.k]].uppercase(), 270f, 22f), 22f, 68f, 22f, v2)
                r.set(22f, 80f, 286f, 110f); c.drawBitmap(pixS.bmp, null, r, bp)
                r.set(300f, 28f, 418f, 117f); fp.color = 0xFF000000.toInt(); c.drawRect(r, fp); c.drawBitmap(pixF.bmp, null, r, bp)
            }
            it.k in setOf("scheme", "color", "col2", "col2m", "peak") -> {
                text(c, it.label.uppercase(), 22f, 64f, 16f, vc)
                text(c, it.values[o[it.k]].uppercase(), 22f, 92f, 24f, v2)
                val sh = floatArrayOf(1f, .75f, .5f, .28f)
                for (i in 0 until 4) { fp.color = Col.argb(Col.scale(vc, sh[i])); c.drawRect(300f + i * 30, 50f, 326f + i * 30, 76f, fp)
                    fp.color = Col.argb(Col.scale(v2, sh[i])); c.drawRect(300f + i * 30, 84f, 326f + i * 30, 110f, fp) }
            }
            else -> {
                text(c, fit(it.label.uppercase(), 396f, 18f), 22f, 66f, 18f, vc)
                val v = when (it.t) { T.POT -> if (o[it.k] == 1) L("AN", "ON") else L("AUS", "OFF"); T.ROLL -> it.values[o[it.k].coerceIn(0, it.values.size - 1)].uppercase(); T.ACT -> L("A ▶ AUSFÜHREN", "A ▶ RUN"); T.INFO -> (it.sub ?: "").uppercase() }
                val arrows = it.t == T.POT || it.t == T.ROLL
                text(c, (if (arrows) "◀ " else "") + fit(v, 340f, if (it.t == T.INFO) 16f else 26f) + (if (arrows) " ▶" else ""), 22f, 102f, if (it.t == T.INFO) 16f else 26f, v2)
                if (it.k == "addfolder") text(c, "${o.added().size} " + L("EIGENE", "ADDED"), 418f, 66f, 12f, vc, align = Paint.Align.RIGHT, alpha = 170)
            }
        }
        c.restore()
        vfdOverlay(c, dl, dt0, dr, db, light = true)
        // ----- Bedienfeld -----
        val top = 130f; val bot = LH - 6f
        if (lvl == "cat") {
            val cw = (LW - 28) / 2; val ch = (bot - top) / ((cats.size + 1) / 2)
            cats.forEachIndexed { i, ct ->
                val cx = 10 + (i % 2) * (cw + 8); val cy = top + (i / 2) * ch
                val sel = i == ocur
                fp.color = if (sel) Col.argb(0x05080A) else 0x22000000; r.set(cx, cy + 2, cx + cw, cy + ch - 2); c.drawRoundRect(r, 6f, 6f, fp)
                if (sel) { fp.style = Paint.Style.STROKE; fp.strokeWidth = 2f; fp.color = Col.argb(vc); c.drawRoundRect(r, 6f, 6f, fp); fp.style = Paint.Style.FILL }
                text(c, ct.icon, cx + 12, cy + ch / 2 + 7, 18f, v2, sansB, glow = sel)
                text(c, ct.name, cx + 38, cy + ch / 2 + 6, 16f, if (sel) vc else 0x15181B, if (sel) mono else sansBI, glow = sel)
                text(c, "${ct.items.size}", cx + cw - 10, cy + ch / 2 + 5, 11f, if (sel) v2 else 0x5D6875, sansB, glow = false, align = Paint.Align.RIGHT)
            }
        } else {
            val items = cats[cat].items; val rh = 38f
            val target = (ocur * rh - (bot - top) / 2 + rh / 2).coerceIn(0f, max(0f, items.size * rh - (bot - top)))
            optScroll += (target - optScroll) * min(1f, dt * 12)
            c.save(); c.clipRect(0f, top, LW, bot); c.translate(0f, top - optScroll)
            items.forEachIndexed { i, itm ->
                val y = i * rh
                if (y + rh < optScroll - 2 || y > optScroll + (bot - top) + 2) return@forEachIndexed
                if (i == ocur) {
                    fp.shader = LinearGradient(0f, 0f, LW, 0f, 0x1A000000, 0, Shader.TileMode.CLAMP); c.drawRect(0f, y, LW, y + rh, fp); fp.shader = null
                    fp.color = Col.argb(vc); path.reset(); path.moveTo(6f, y + rh / 2 - 7); path.lineTo(16f, y + rh / 2); path.lineTo(6f, y + rh / 2 + 7); path.close(); c.drawPath(path, fp)
                }
                fp.color = 0x18000000; c.drawRect(0f, y + rh - 1, LW, y + rh, fp); fp.color = 0xAAFFFFFF.toInt(); c.drawRect(0f, y + rh, LW, y + rh + 1, fp)
                val sub = if (itm.k == "addfolder") "${o.added().size} " + L("eigene Ordner", "added folders") else itm.sub
                text(c, itm.label, 24f, y + (if (sub != null) 18 else 24), 14f, 0x15181B, sansBI, glow = false)
                if (sub != null) text(c, sub, 24f, y + 31, 10f, 0x5D6875, Typeface.SANS_SERIF, glow = false)
                when (itm.t) {
                    T.POT -> drawPot(c, itm.k, LW - 14, y + rh / 2, o[itm.k] == 1, dt)
                    T.ROLL -> drawRoll(c, itm, LW - 14 - 140, y + 4, dt)
                    T.ACT -> drawKey(c, LW - 14, y + rh / 2, i == ocur)
                    T.INFO -> {}
                }
            }
            c.restore()
        }
    }

    private fun approach(key: String, target: Float, dt: Float, speed: Float = 14f): Float {
        val v = anim[key] ?: target; val n = v + (target - v) * min(1f, dt * speed); anim[key] = n; return n
    }

    private fun drawPot(c: Canvas, k: String, right: Float, cy: Float, on: Boolean, dt: Float) {
        val ledX = right - 4; val cx = right - 38
        text(c, L("AN", "ON"), cx + 18, cy + 3, 8f, 0x5D6875, sansB, glow = false)
        text(c, L("AUS", "OFF"), cx - 18, cy + 3, 8f, 0x5D6875, sansB, glow = false, align = Paint.Align.RIGHT)
        fp.color = 0xFF59606B.toInt(); c.drawCircle(cx, cy + 1, 16f, fp)
        fp.shader = SweepGradient(cx, cy, intArrayOf(0xFFF4F6F8.toInt(), 0xFF8D9298.toInt(), 0xFFE9ECEF.toInt(), 0xFF7D8288.toInt(), 0xFFF4F6F8.toInt(), 0xFF9AA0A5.toInt(), 0xFFF4F6F8.toInt()), null)
        c.drawCircle(cx, cy, 14f, fp); fp.shader = null
        val ang = approach("pot$k", if (on) 60f else -60f, dt, 10f)
        c.save(); c.rotate(ang, cx, cy)
        fp.color = if (on) Col.argb(vc) else 0xFF25292E.toInt(); c.drawRoundRect(cx - 1.5f, cy - 11, cx + 1.5f, cy - 3, 1.5f, 1.5f, fp)
        c.restore()
        fp.color = if (on) Col.argb(vc) else 0xFF3A3F45.toInt()
        if (on) fp.setShadowLayer(4f, 0f, 0f, Col.argb(glowC)) else fp.clearShadowLayer()
        c.drawCircle(ledX, cy, 3.5f, fp); fp.clearShadowLayer()
    }

    private fun drawRoll(c: Canvas, it: Item, x: Float, y: Float, dt: Float) {
        val w = 140f; val h = 30f
        fp.color = 0xFFFFFFFF.toInt(); r.set(x, y + 1, x + w, y + h + 1); c.drawRoundRect(r, 4f, 4f, fp)
        fp.color = Col.argb(0x07040D); r.set(x, y, x + w, y + h); c.drawRoundRect(r, 4f, 4f, fp)
        val pos = approach("roll${it.k}", o[it.k].toFloat(), dt, 12f)
        c.save(); c.clipRect(x, y, x + w, y + h)
        val lo = max(0, pos.toInt() - 2); val hi = min(it.values.size - 1, pos.toInt() + 2)
        for (i in lo..hi) {
            val yy = y + h / 2 + (i - pos) * 16f + 4
            val col = if (it.colored && i < Palette.COLORS.size && !(it.k == "col2" && i >= Palette.RAINBOW)) Palette.COLORS[i].c else vc
            val a = (255 * (1 - min(1f, abs(i - pos) * .85f))).toInt().coerceIn(30, 255)
            text(c, it.values[i], x + w / 2, yy, 13f, col, align = Paint.Align.CENTER, alpha = a)
        }
        c.restore()
        fp.color = 0x14FFFFFF; c.drawRect(x + 3, y + 7, x + w - 3, y + 8, fp); c.drawRect(x + 3, y + 23, x + w - 3, y + 24, fp)
        if (o["scanl"] == 1) c.drawRect(x, y, x + w, y + h, scanPaint)
    }

    private fun drawKey(c: Canvas, right: Float, cy: Float, cur: Boolean) {
        val w = 38f
        fp.color = 0xFF6D737A.toInt(); r.set(right - w, cy - 9, right, cy + 11); c.drawRoundRect(r, 4f, 4f, fp)
        fp.shader = LinearGradient(0f, cy - 11, 0f, cy + 9, if (cur) 0xFFFFFFFF.toInt() else 0xFFF4F6F8.toInt(), if (cur) 0xFFCDD3D9.toInt() else 0xFFAEB4BA.toInt(), Shader.TileMode.CLAMP)
        r.set(right - w, cy - 11, right, cy + 9); c.drawRoundRect(r, 4f, 4f, fp); fp.shader = null
        text(c, "A ▶", right - w / 2, cy + 3, 10f, 0x222222, sansB, glow = false, align = Paint.Align.CENTER)
    }

    // ---------- Fenster ----------
    private var neo: List<Pair<String, String>> = emptyList()
    private fun drawWin(c: Canvas, now: Long) {
        if (win == "about") { drawNeofetch(c); return }
        vfdBox(c, 18f, 22f, 422f, 308f)
        fp.color = Col.argb(vc); c.drawRect(18f, 22f, 422f, 46f, fp)
        val (title, body, foot) = winContent(now)
        text(c, "[ $title ]", 28f, 40f, 16f, 0x05080A, glow = false)
        var y = 74f
        body.split('\n').forEach { line ->
            if (line.startsWith("§BAR")) { val p = line.removePrefix("§BAR").toFloat()
                fp.style = Paint.Style.STROKE; fp.color = Col.argb(vc); c.drawRect(30f, y - 14, 410f, y + 2, fp); fp.style = Paint.Style.FILL
                var x = 32f; while (x < 32 + 376 * p) { c.drawRect(x, y - 12, min(x + 7, 32 + 376 * p), y, fp); x += 9 }
            } else text(c, line, 30f, y, 17f, vc)
            y += 23f
        }
        fp.color = Col.argb(vc, 90); var x = 18f; while (x < 422) { c.drawRect(x, 280f, x + 4, 281f, fp); x += 7 }
        text(c, foot, 28f, 300f, 15f, v2)
        vfdOverlay(c, 18f, 22f, 422f, 308f, light = true)
    }

    private fun drawNeofetch(c: Canvas) {
        vfdBox(c, 8f, 8f, 432f, 322f)
        c.save(); c.clipRect(10f, 10f, 430f, 320f)
        val fs = 14f; val lh = 18.5f
        // App-Icon mit Android-Hut (feste Logo-Farben)
        val lp = 5.6f; val lx = 20f; val ly = 40f
        SysInfo.LOGO.forEachIndexed { r0, row -> row.forEachIndexed { c0, ch -> SysInfo.LOGO_COLORS[ch]?.let { col ->
            fp.shader = null; fp.color = Col.argb(col); c.drawRect(lx + c0 * lp, ly + r0 * lp, lx + (c0 + 1) * lp, ly + (r0 + 1) * lp, fp) } } }
        var y = 30f
        neo.forEach { (k, v) ->
            if (k.isEmpty()) text(c, v, 168f, y, fs, if (v.startsWith("-")) vc else v2)
            else { val vx = 168f + tw("Speicher: ", fs); text(c, "$k:", 168f, y, fs, v2); text(c, fit(v, 426f - vx, fs), vx, y, fs, vc) }
            y += lh
        }
        // Farbpalette wie bei neofetch – nur Haupt- und Zweitfarbe in Abstufungen
        val shades = floatArrayOf(1f, .75f, .5f, .28f)
        for (i in 0 until 4) { fp.color = Col.argb(Col.scale(vc, shades[i])); c.drawRect(168f + i * 22, y - 8, 188f + i * 22, y + 6, fp)
            fp.color = Col.argb(Col.scale(v2, shades[i])); c.drawRect(256f + i * 22, y - 8, 276f + i * 22, y + 6, fp) }
        text(c, L("B = ZURÜCK", "B = BACK"), 14f, 310f, 13f, v2)
        c.restore()
        vfdOverlay(c, 8f, 8f, 432f, 322f, light = true)
    }

    private fun fit(s: String, w: Float, size: Float): String {
        if (tw(s, size) <= w) return s
        var t = s; while (t.length > 1 && tw("$t…", size) > w) t = t.dropLast(1); return "$t…"
    }

    private fun winContent(now: Long): Triple<String, String, String> {
        val nF = Engine.folders.size; val nT = Engine.folders.sumOf { it.tracks.size }
        val types = L("ALLE FORMATE", "ALL FORMATS")
        val fT = L("ORDNER", "FOLDERS"); val lT = L("LIEDER", "TRACKS"); val back = L("B = ZURÜCK", "B = BACK")
        return when (win) {
            "scan" -> if (Engine.scanning) Triple(L("EINLESEN", "SCANNING"), "${Engine.scanInfo.uppercase().take(34)}\n§BAR${((now / 120) % 20) / 20f}\n$types", L("BITTE WARTEN …", "PLEASE WAIT …"))
                      else Triple(L("EINLESEN", "SCANNING"), L("FERTIG.", "DONE.") + "\n§BAR1\n$nF $fT · $nT $lT\n\n$types", "A = OK")
            "keys" -> Triple(L("TASTEN", "BUTTONS"), L("A ...... PLAY / PAUSE\nB ...... ORDNER (BASH)\nSTART .. OPTIONEN\nSELECT . VOLLBILD AN/AUS\nL / R .. VISUALIZER WECHSELN\n▲▼ ..... WALZE / ORDNER\n◀▶ ..... LIED (HALTEN=SPULEN)\nX / Y .. ZUFALL / WIEDERH.",
                                                      "A ...... PLAY / PAUSE\nB ...... FOLDERS (BASH)\nSTART .. OPTIONS\nSELECT . FULL SCREEN ON/OFF\nL / R .. SWITCH VISUALIZER\n▲▼ ..... DRUM / FOLDER\n◀▶ ..... TRACK (HOLD=SEEK)\nX / Y .. SHUFFLE / REPEAT"), back)
            "reset" -> Triple(L("WERKSEINSTELLUNGEN", "FACTORY RESET"), L("ALLE OPTIONEN ZURÜCK-\nSETZEN?\n\nDEINE MUSIK UND EIGENEN\nORDNER BLEIBEN.", "RESET ALL OPTIONS?\n\nYOUR MUSIC AND ADDED\nFOLDERS STAY."), L("A = JA · B = NEIN", "A = YES · B = NO"))
            "empty" -> Triple(L("KEINE MUSIK", "NO MUSIC"), L("KEINE LIEDER GEFUNDEN.\n\nMUSIK NACH /Music KOPIEREN\nODER UNTER START › BIBLIOTHEK\nEINEN ORDNER HINZUFÜGEN.", "NO TRACKS FOUND.\n\nCOPY MUSIC TO /Music\nOR ADD A FOLDER UNDER\nSTART › LIBRARY."), L("A = NEU SUCHEN · B = ZURÜCK", "A = RESCAN · B = BACK"))
            "noperm" -> Triple(L("KEIN ZUGRIFF", "NO ACCESS"), L("OHNE DIE BERECHTIGUNG\n„MUSIK UND AUDIO“ KANN\nICH NICHTS LESEN.\n\nEINSTELLUNGEN › APPS ›\nP-HR-X1 › BERECHTIGUNGEN", "WITHOUT THE PERMISSION\n\"MUSIC AND AUDIO\" I CAN'T\nREAD ANYTHING.\n\nSETTINGS › APPS ›\nP-HR-X1 › PERMISSIONS"), back)
            "keytest" -> Triple(L("TASTENTEST", "BUTTON TEST"), L("DRÜCK EINE TASTE:", "PRESS A BUTTON:") + "\n" + keyLog.takeLast(7).joinToString("\n"), L("3x DIESELBE TASTE = ENDE", "3x SAME BUTTON = EXIT"))
            else -> Triple("", "", "")
        }
    }

    // ---------- Vollbild-Visualizer ----------
    private fun drawViz(c: Canvas) {
        fp.color = 0xFF000000.toInt(); c.drawRect(0f, 0f, LW, LH, fp)
        r.set(0f, 0f, LW, LH); c.drawBitmap(pixF.bmp, null, r, bp)
        if (o["vizinfo"] == 1) {
            vfdBox(c, 6f, 6f, 434f, 28f, 4f)
            val t = Engine.track()
            c.save(); c.clipRect(8f, 6f, 360f, 28f)
            text(c, if (t != null) ("F%02d T%02d · ".format(Engine.cur + 1, Engine.index() + 1) + (if (t.artist.isBlank()) t.title else "${t.artist} — ${t.title}")).uppercase() else L("KEINE MUSIK", "NO MUSIC"), 14f, 22f, 12f, vc)
            c.restore()
            text(c, (if (Engine.playing()) "▶ " else "❚❚ ") + fmtTime(Engine.pos()), 428f, 22f, 12f, v2, align = Paint.Align.RIGHT)
        }
    }

    // ---------- Startanimation (VFD-Test) ----------
    private fun drawBoot(c: Canvas, now: Long) {
        val t = now - bootStart
        fp.color = 0xFF000000.toInt(); c.drawRect(0f, 0f, LW, LH, fp)
        vfdBox(c, 10f, 10f, 430f, 320f)
        val l1 = BRAND
        when {
            t < 700 -> { text(c, "88:88 8888", 220f, 150f, 40f, vc, align = Paint.Align.CENTER); text(c, "████████████████", 220f, 180f, 16f, vc, align = Paint.Align.CENTER)
                text(c, "MP3 FLAC WAV DSD HI-RES RDM RPT SLP", 220f, 205f, 13f, v2, align = Paint.Align.CENTER) }
            t < 900 -> {}
            else -> {
                val n = min(l1.length, ((t - 900) / 60).toInt())
                text(c, l1.take(n), 220f, 160f, 40f, vc, align = Paint.Align.CENTER)
                if (t > 1900) text(c, "HIGH RESOLUTION AUDIO", 220f, 190f, 16f, v2, align = Paint.Align.CENTER)
                if (t > 2600) text(c, "vibecoded with Claude", 220f, 300f, 11f, vc, align = Paint.Align.CENTER, alpha = 140)
                if (t > 2600) text(c, "♫ ${Engine.folders.size} " + L("ORDNER", "FOLDERS") + " · ${Engine.folders.sumOf { it.tracks.size }} " + L("LIEDER", "TRACKS"), 220f, 215f, 13f, vc, align = Paint.Align.CENTER, alpha = 180)
            }
        }
        vfdOverlay(c, 10f, 10f, 430f, 320f)
    }

    private fun drawOsd(c: Canvas) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC); val mx = max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        vfdBox(c, 110f, 168f, 330f, 212f)
        text(c, "VOL", 120f, 195f, 14f, vc)
        val lit = (v * 20f / mx).toInt()
        for (i in 0 until 20) { fp.color = Col.argb(if (i < lit) vc else Col.scale(vc, .25f)); c.drawRect(158f + i * 7.4f, 182f, 158f + i * 7.4f + 5.4f, 198f, fp) }
        text(c, "%02d".format(v), 322f, 195f, 14f, v2, align = Paint.Align.RIGHT)
    }

    private fun drawToast(c: Canvas) {
        val w = tw(toastText, 13f) + 24
        fp.color = Col.argb(vc); c.drawRect(220 - w / 2, 26f, 220 + w / 2, 44f, fp)
        text(c, toastText, 220f, 40f, 13f, 0x05080A, glow = false, align = Paint.Align.CENTER)
    }

    private fun fmtTime(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    private fun toast(s: String) {
        if (mode == "player") { dmsg = s; dmsgUntil = System.currentTimeMillis() + 1400; return }
        toastText = s; toastUntil = System.currentTimeMillis() + 1300
    }

    // ================= Tasten =================
    fun openWin(name: String) { if (mode != "win") prev = mode; mode = "win"; win = name; if (name == "about") neo = SysInfo.lines(context) }
    private fun closeWin() { mode = if (prev == "win") "player" else prev; win = "" }

    fun keyTest(code: Int, ev: KeyEvent): Boolean {
        if (!(mode == "win" && win == "keytest")) return false
        keyLog += "%-16s %d".format(KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"), code)
        val last = keyLog.takeLast(3)
        if (last.size == 3 && last.all { it == last[0] }) { keyLog.clear(); closeWin() }
        return true
    }

    fun press(k: String, repeat: Boolean) {
        lastInput = System.currentTimeMillis()
        val n = Engine.folders.size
        if (k == "vup" || k == "vdn") {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (k == "vup") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            osdUntil = lastInput + 1200; return
        }
        if (mode == "boot") { mode = "player"; return }
        when (mode) {
            "win" -> {
                when {
                    win == "reset" && k == "a" -> { o.reset(); Engine.applyOpts(); closeWin(); needRescan = true; toast(L("WERKSEINSTELLUNGEN GELADEN", "FACTORY SETTINGS LOADED")) }
                    win == "empty" && k == "a" -> { Engine.rescan(); win = "scan" }
                    win == "scan" && Engine.scanning -> {}
                    k == "a" || k == "b" || k == "start" -> closeWin()
                }
                return
            }
            "viz" -> when (k) {
                "select" -> { mode = "player"; return }
                "b" -> { mode = "term"; tcur = Engine.cur; return }
                "start" -> { openOpt(); return }
            }
            "term" -> {
                if (n == 0) { mode = "player"; return }
                when (k) {
                    "up" -> tcur = (tcur - 1 + n) % n
                    "down" -> tcur = (tcur + 1) % n
                    "l1" -> tcur = max(0, tcur - 10)
                    "r1" -> tcur = min(n - 1, tcur + 10)
                    "left" -> { termMsg = "p-hr-x1:/Music$ cd ..   " + L("(/Music ist oben)", "(/Music is top)"); postDelayed({ termMsg = null }, 1200) }
                    "a", "right" -> { val f = Engine.folders[tcur]; termMsg = "▶ " + L("spiele", "play") + " \"${f.name}\" · ${f.tracks.size} " + L("Lieder", "tracks")
                        val sel = tcur
                        postDelayed({ termMsg = null; mode = "player"; drum.set(sel, n); Engine.playFolder(sel) }, 450) }
                    "b" -> mode = "player"
                    "start" -> openOpt()
                    "select" -> mode = "viz"
                }
                return
            }
            "opt" -> { optKey(k); return }
            "browse" -> { browseKey(k); return }
        }
        // Hauptbild
        when (k) {
            "a" -> { if (n == 0) { openWin(if (Engine.scanning) "scan" else "empty"); return }; Engine.toggle() }
            "left", "right" -> holdSeek(k, repeat)
            "up", "down" -> if (n > 0) { val i = (Engine.cur + (if (k == "up") 1 else -1) + n) % n; drum.set(i, n); Engine.playFolder(i) }
            "b" -> { mode = "term"; tcur = Engine.cur }
            "start" -> openOpt()
            "select" -> { mode = "viz"; toast("VISUALIZER: " + Viz.label(o["viz"]).uppercase()) }
            "l1", "r1" -> nextViz(if (k == "r1") 1 else -1)
            "x" -> { o["shuf"] = 1 - o["shuf"]; Engine.applyOpts() }
            "y" -> { o["rep"] = (o["rep"] + 1) % 3; Engine.applyOpts() }
        }
    }

    // kurz drücken = Lied wechseln (beim Loslassen), halten = spulen (wird schneller)
    private var pendStep: String? = null; private var seeking = false; private var holdStart = 0L; private var lastSeek = 0L
    private fun holdSeek(k: String, repeat: Boolean) {
        val now = System.currentTimeMillis()
        if (!repeat) { pendStep = k; seeking = false; holdStart = now; return }
        if (now - lastSeek < 110) return
        lastSeek = now; seeking = true
        val held = now - holdStart; val stepMs = if (held > 4000) 12000L else if (held > 2000) 6000L else 3000L
        Engine.seekBy(if (k == "left") -stepMs else stepMs)
        seekDir = if (k == "left") -1 else 1; seekShowUntil = now + 500
    }
    fun release(k: String) {
        if ((k == "left" || k == "right") && pendStep == k) { if (!seeking && (mode == "player" || mode == "viz")) Engine.step(if (k == "left") -1 else 1); pendStep = null; seeking = false }
    }

    private fun nextViz(d: Int) {
        o["viz"] = (o["viz"] + d + Viz.NAMES.size) % Viz.NAMES.size
        toast("VISUALIZER: " + Viz.label(o["viz"]).uppercase())
    }

    private fun openOpt() { mode = "opt"; lvl = "cat"; ocur = 0; optScroll = 0f }
    private fun closeOpt() {
        mode = "player"
        if (needRescan) { needRescan = false; Engine.rescan { drum.jump(Engine.cur); toast(L("BIBLIOTHEK AKTUALISIERT", "LIBRARY UPDATED")) } }
    }

    private fun optKey(k: String) {
        val cats = Menu.CATS
        if (lvl == "cat") {
            val n = cats.size
            when (k) {
                "up" -> ocur = (ocur - 2 + n) % n
                "down" -> ocur = (ocur + 2) % n
                "left" -> ocur = if (ocur % 2 == 1) ocur - 1 else ocur
                "right" -> ocur = if (ocur % 2 == 0 && ocur + 1 < n) ocur + 1 else ocur
                "a" -> { cat = ocur; lvl = "sub"; ocur = 0; optScroll = 0f }
                "b", "start" -> closeOpt()
            }
            return
        }
        val items = cats[cat].items; val it = items[ocur]
        when (k) {
            "up" -> ocur = (ocur - 1 + items.size) % items.size
            "down" -> ocur = (ocur + 1) % items.size
            "left" -> change(it, -1)
            "right" -> change(it, 1)
            "a" -> when (it.t) { T.POT -> change(it, if (o[it.k] == 1) -1 else 1); T.ROLL -> { o[it.k] = (o[it.k] + 1) % it.values.size; afterChange(it) }; T.ACT -> action(it); T.INFO -> {} }
            "b" -> { lvl = "cat"; ocur = cat; optScroll = 0f }
            "start" -> closeOpt()
        }
    }

    private fun change(it: Item, d: Int) {
        when (it.t) {
            T.ACT -> { if (d > 0) action(it); return }
            T.INFO -> return
            T.POT -> o[it.k] = if (d > 0) 1 else 0
            T.ROLL -> o[it.k] = (o[it.k] + d).coerceIn(0, it.values.size - 1)
        }
        afterChange(it)
    }

    private fun afterChange(it: Item) {
        o.hook(it.k)
        if (it.k in setOf("src", "sub", "sort", "hidden")) needRescan = true
        Engine.applyOpts()
    }

    private fun action(it: Item) {
        when (it.action) {
            "scan" -> { needRescan = false; Engine.rescan { drum.jump(Engine.cur) }; openWin("scan") }
            "browse" -> { bStack.clear(); bcur = 0; mode = "browse" }
            "boot" -> { bootStart = System.currentTimeMillis(); prev = "opt"; mode = "boot" }
            "viz" -> mode = "viz"
            "keys", "about", "reset" -> openWin(it.action)
            "keytest" -> { keyLog.clear(); openWin("keytest") }
            "quit" -> (context as? MainActivity)?.quitApp()
        }
    }

    // ================= Touch (abschaltbar unter System) =================
    private var tdX = 0f; private var tdY = 0f; private var tdT = 0L
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (o["touch"] == 0) return true
        if (sc <= 0f) return true
        val lx = (e.x - ox) / sc; val ly = (e.y - oy) / sc
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { tdX = lx; tdY = ly; tdT = System.currentTimeMillis() }
            android.view.MotionEvent.ACTION_UP -> {
                lastInput = System.currentTimeMillis()
                val dx = lx - tdX; val dy = ly - tdY
                val swipe = if (abs(dx) > 40 && abs(dx) > abs(dy)) (if (dx > 0) "right" else "left") else if (abs(dy) > 40) (if (dy > 0) "down" else "up") else null
                if (swipe != null) swipeAt(swipe, tdX, tdY) else tapAt(tdX, tdY)
                performClick()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun winStart(cur: Int, size: Int, rows: Int) = (cur - rows / 2).coerceIn(0, max(0, size - rows))

    private fun swipeAt(dir: String, x: Float, y: Float) {
        when (mode) {
            "player" -> when { y >= 215f && (dir == "up" || dir == "down") -> press(if (dir == "up") "up" else "down", false)
                               dir == "left" || dir == "right" -> { press(dir, false); release(dir) }
                               else -> press(if (dir == "up") "up" else "down", false) }
            "viz" -> when (dir) { "left" -> nextViz(-1); "right" -> nextViz(1); else -> press(dir, false) }
            "term", "browse", "opt" -> if (dir == "up" || dir == "down") repeat(3) { press(if (dir == "up") "down" else "up", false) } else press(if (dir == "left") "left" else "right", false)
            else -> press("b", false)
        }
    }

    private fun tapAt(x: Float, y: Float) {
        when (mode) {
            "boot" -> press("a", false)
            "player" -> when {
                y < 24f -> press("start", false)
                y in 135f..190f -> press("select", false)
                y < 215f -> press("a", false)
                else -> press("b", false)
            }
            "viz" -> press("select", false)
            "win" -> if (win != "keytest") press(if (win == "scan" && !Engine.scanning) "a" else "b", false)
            "term" -> {
                if (y > 262f) { press("b", false); return }
                val n = Engine.folders.size; val st = winStart(tcur, n, 8); val row = ((y - 34f + 19f) / TLH).toInt() - 1
                val i = st + row
                if (row >= 0 && i in 0 until n) { if (i == tcur) press("a", false) else tcur = i }
            }
            "browse" -> {
                if (y > 262f) { press("b", false); return }
                val es = bEntries(); val st = winStart(bcur, es.size, 9); val row = ((y - 34f + 19f) / TLH).toInt() - 1
                val i = st + row
                if (row >= 0 && i in es.indices) {
                    if (i != bcur) { bcur = i; return }
                    if (es[i] != null && x in 80f..170f) press("a", false) else press("right", false)
                }
            }
            "opt" -> optTap(x, y)
        }
    }

    private fun optTap(x: Float, y: Float) {
        if (y < 24f) { press("start", false); return }
        if (y < 125f) { press(if (lvl == "cat") "start" else "b", false); return }
        val top = 130f; val bot = LH - 6f
        if (lvl == "cat") {
            val cw = (LW - 28) / 2; val ch = (bot - top) / ((Menu.CATS.size + 1) / 2)
            val col = if (x < 10 + cw + 4) 0 else 1; val row = ((y - top) / ch).toInt()
            val i = row * 2 + col
            if (i in Menu.CATS.indices) { ocur = i; press("a", false) }
            return
        }
        val items = Menu.CATS[cat].items
        val i = ((y - top + optScroll) / 38f).toInt()
        if (i !in items.indices) return
        val wasSel = i == ocur; ocur = i; val it = items[i]
        val inCtl = x > LW - 170f
        when (it.t) {
            T.POT -> if (inCtl || wasSel) change(it, if (o[it.k] == 1) -1 else 1)
            T.ROLL -> if (inCtl) change(it, if (x < LW - 14 - 70) -1 else 1) else if (wasSel) change(it, 1)
            T.ACT -> if (inCtl || wasSel) action(it)
            T.INFO -> {}
        }
    }

    // ================= Texturen =================
    private fun makeAlu(): Bitmap {
        val w = 440; val h = 330; val px = IntArray(w * h); val rnd = Random(4)
        val stops = floatArrayOf(0f, .30f, .55f, .75f, 1f)
        val cols = intArrayOf(0xC9D6E0, 0xEEF4F8, 0xCFDBE4, 0xF4F8FB, 0xC2CFDA)
        val row = FloatArray(h) { rnd.nextFloat() * 2 - 1 }
        val streak = FloatArray(w * 2) { rnd.nextFloat() * 2 - 1 }
        for (y in 0 until h) for (x in 0 until w) {
            val t = ((x + y * .36f) / (w + h * .36f)).coerceIn(0f, 1f)
            var i = 0; while (i < 3 && t > stops[i + 1]) i++
            val base = Col.mix(cols[i], cols[i + 1], (t - stops[i]) / (stops[i + 1] - stops[i]))
            val n = row[y] * 6 + streak[(x / 23 + y * 7) % streak.size] * 3
            px[y * w + x] = Col.argb(Col.rgb(Col.r(base) + n.toInt(), Col.g(base) + n.toInt(), Col.b(base) + n.toInt()))
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
    private fun makeDots(): Bitmap {
        val d = 0x05080A
        val a = intArrayOf(200, 90, 200, 90, 0, 90, 200, 90, 200)
        return Bitmap.createBitmap(IntArray(9) { Col.argb(d, a[it]) }, 3, 3, Bitmap.Config.ARGB_8888)
    }
    private fun makeScan() = Bitmap.createBitmap(intArrayOf(0, 0, Col.argb(0, 0x4A)), 1, 3, Bitmap.Config.ARGB_8888)
}
