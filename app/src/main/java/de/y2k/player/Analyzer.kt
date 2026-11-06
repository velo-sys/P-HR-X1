package de.y2k.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Daten, die die Visualizer lesen (geglättet, 0..1). */
class Audio {
    val b = FloatArray(32)
    val w = FloatArray(128)
    var L = 0f
    var R = 0f
    var kick = 0f
    var beatN = 0
}

/**
 * Hängt am PCM-Strom von ExoPlayer (TeeAudioProcessor) – kein Mikrofon-Recht nötig.
 * Rechnet FFT -> 32 Bänder, Wellenform, L/R-Pegel und erkennt Beats am Bass.
 */
@UnstableApi
class Analyzer : TeeAudioProcessor.AudioBufferSink {
    private var sr = 44100
    private var ch = 2
    private var enc = C.ENCODING_PCM_16BIT
    private val N = 1024
    private val ring = FloatArray(N)
    private var rpos = 0
    private var sinceFft = 0
    private var sumL = 0.0
    private var sumR = 0.0
    private var cnt = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val win = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private var bassAvg = .3f
    private var lastBeat = 0L

    // veröffentlichte Rohwerte (Lesen im UI-Thread)
    private val lock = Any()
    private val tb = FloatArray(32)
    private val tw = FloatArray(128)
    private var tL = 0f
    private var tR = 0f
    @Volatile var beatN = 0; private set
    @Volatile var beatTime = 0L; private set
    @Volatile var lastData = 0L; private set

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sr = sampleRateHz; ch = max(1, channelCount); enc = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val bps = when (enc) { C.ENCODING_PCM_8BIT -> 1; C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4; else -> return }
        val frame = bps * ch
        while (b.remaining() >= frame) {
            var mono = 0f
            for (c in 0 until ch) {
                val v = read(b, bps)
                mono += v
                if (c == 0) sumL += (v * v).toDouble()
                if (c == 1 || ch == 1) sumR += (v * v).toDouble()
            }
            cnt++
            ring[rpos] = mono / ch; rpos = (rpos + 1) % N
            if (++sinceFft >= 512) { sinceFft = 0; analyze() }
        }
    }

    private fun read(b: ByteBuffer, bps: Int): Float = when (bps) {
        1 -> ((b.get().toInt() and 255) - 128) / 128f
        2 -> b.short / 32768f
        3 -> { val lo = b.get().toInt() and 255; val mid = b.get().toInt() and 255; val hi = b.get().toInt(); ((hi shl 16) or (mid shl 8) or lo) / 8388608f }
        else -> if (enc == C.ENCODING_PCM_FLOAT) b.float else b.int / 2147483648f
    }

    private fun analyze() {
        for (i in 0 until N) { re[i] = ring[(rpos + i) % N] * win[i]; im[i] = 0f }
        fft(re, im)
        val nyq = sr / 2f
        val fLo = 40f; val fHi = min(16000f, nyq * .95f)
        val bands = FloatArray(32)
        for (k in 0 until 32) {
            val f0 = fLo * (fHi / fLo).pow(k / 32f); val f1 = fLo * (fHi / fLo).pow((k + 1) / 32f)
            val i0 = max(1, (f0 / nyq * (N / 2)).toInt()); val i1 = max(i0 + 1, (f1 / nyq * (N / 2)).toInt())
            var m = 0f
            for (i in i0 until min(i1, N / 2)) m = max(m, sqrt(re[i] * re[i] + im[i] * im[i]))
            val db = 20f * log10(m / (N / 4f) + 1e-9f)
            bands[k] = clamp01((db + 62f + k * .45f) / 58f)
        }
        val wave = FloatArray(128) { j -> (ring[(rpos + N - 512 + j * 4) % N] * 1.6f).coerceIn(-1f, 1f) }
        val rl = sqrt(sumL / max(1, cnt)).toFloat(); val rr = sqrt(sumR / max(1, cnt)).toFloat()
        sumL = 0.0; sumR = 0.0; cnt = 0
        val bass = (bands[0] + bands[1] + bands[2] + bands[3]) / 4f
        val now = System.nanoTime() / 1_000_000L
        if (bass > bassAvg * 1.22f && bass > .32f && now - lastBeat > 260) { lastBeat = now; beatN++; beatTime = now }
        bassAvg = bassAvg * .94f + bass * .06f
        synchronized(lock) {
            System.arraycopy(bands, 0, tb, 0, 32); System.arraycopy(wave, 0, tw, 0, 128)
            tL = clamp01((20f * log10(rl + 1e-6f) + 48f) / 46f); tR = clamp01((20f * log10(rr + 1e-6f) + 48f) / 46f)
        }
        lastData = now
    }

    /** Überträgt in die geglättete Anzeige (UI-Takt 20/s). sm: 0 weich, 1 normal, 2 zackig */
    fun pull(into: Audio, playing: Boolean, sm: Int) {
        val (att, dec) = when (sm) { 0 -> .3f to .1f; 2 -> .95f to .6f; else -> .6f to .25f }
        val now = System.nanoTime() / 1_000_000L
        val live = playing && now - lastData < 400
        synchronized(lock) {
            for (i in 0 until 32) { val t = if (live) tb[i] else 0f; into.b[i] += (t - into.b[i]) * (if (t > into.b[i]) att else dec) }
            for (j in 0 until 128) into.w[j] = if (live) into.w[j] + (tw[j] - into.w[j]) * .7f else into.w[j] * .8f
            into.L += ((if (live) tL else 0f) - into.L) * .4f
            into.R += ((if (live) tR else 0f) - into.R) * .4f
        }
        into.beatN = beatN
        into.kick = if (live) exp(-(now - beatTime) / 160f).toFloat() else into.kick * .8f
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

}
