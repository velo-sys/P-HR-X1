package de.y2k.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/** Zentrale: Player, Bibliothek, Einstellungen. Lebt im App-Prozess (Activity + Service teilen sie). */
@UnstableApi
object Engine {
    lateinit var app: Context; private set
    lateinit var opts: Opts; private set
    val analyzer = Analyzer()
    private var exo: ExoPlayer? = null
    private val main = Handler(Looper.getMainLooper())

    var folders: List<Folder> = emptyList(); private set
    var tree: Dir = Dir("/", ""); private set
    var cur = 0; private set
    var scanning = false; private set
    var scanInfo = ""; private set
    var fmt = Triple("", "", ""); private set
    var hires = false; private set
    var onChange: (() -> Unit)? = null
    var errorMsg = ""; var errorAt = 0L
    private var errorRun = 0
    private var restored = false

    fun isReady() = ::opts.isInitialized
    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext; opts = Opts(app)
        main.post(tickR)
    }

    fun player(): ExoPlayer {
        exo?.let { return it }
        val rf = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(TeeAudioProcessor(analyzer))).build()
        }
        val p = ExoPlayer.Builder(app, rf)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(opts["unplug"] == 1)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { readFormat(); saveState(); changed() }
            override fun onIsLoadingChanged(isLoading: Boolean) { if (!isLoading && exo?.isPlaying == true) errorRun = 0 }
            override fun onIsPlayingChanged(isPlaying: Boolean) { saveState(); changed() }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) folderEnd(); changed() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMsg = "${track()?.ext ?: "FILE"} " + L("NICHT ABSPIELBAR – ÜBERSPRUNGEN", "NOT PLAYABLE – SKIPPED"); errorAt = System.currentTimeMillis()
                val pl = exo ?: return
                if (++errorRun < pl.mediaItemCount) { pl.seekToNextMediaItem(); pl.prepare(); pl.play() } else errorRun = 0
                changed()
            }
        })
        exo = p
        applyOpts()
        return p
    }

    fun release() { saveState(); exo?.release(); exo = null }
    private fun changed() = onChange?.invoke()

    /* ---------- Bibliothek ---------- */
    fun rescan(done: (() -> Unit)? = null) {
        if (scanning) return
        scanning = true; scanInfo = ""; changed()
        thread(name = "scan") {
            val res = try { Library.scan(app, opts) { rel, n -> scanInfo = "$rel · $n"; main.post { changed() } } } catch (e: SecurityException) { ScanResult(emptyList(), Dir("/", "")) }
            val list = res.folders
            main.post {
                val keepPath = folders.getOrNull(cur)?.path
                folders = list; tree = res.root; scanning = false
                if (!restored) { restored = true; restoreState() }
                else if (keepPath != null) cur = list.indexOfFirst { it.path == keepPath }.coerceAtLeast(0)
                changed(); done?.invoke()
            }
        }
    }

    /* ---------- Wiedergabe ---------- */
    fun track(): Track? = folders.getOrNull(cur)?.tracks?.getOrNull(exo?.currentMediaItemIndex ?: 0)
    fun index() = exo?.currentMediaItemIndex ?: 0
    fun pos() = exo?.currentPosition ?: 0L
    fun dur() = exo?.duration?.takeIf { it > 0 } ?: track()?.durMs ?: 0L
    fun playing() = exo?.isPlaying == true

    fun playFolder(i: Int, track: Int = 0, posMs: Long = 0L, play: Boolean = true) {
        if (folders.isEmpty()) return
        cur = ((i % folders.size) + folders.size) % folders.size
        val f = folders[cur]
        val p = player()
        p.setMediaItems(f.tracks.map {
            MediaItem.Builder().setUri(it.uri).setMediaId(it.uri.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build()
        }, track.coerceIn(0, max(0, f.tracks.size - 1)), posMs)
        p.prepare(); p.playWhenReady = play
        applyOpts(); readFormat(); changed()
    }

    fun toggle() { val p = player(); if (p.mediaItemCount == 0) { playFolder(cur); return }; if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0); p.play() } }
    fun step(d: Int) {
        val p = player(); val n = p.mediaItemCount; if (n == 0) return
        if (opts["shuf"] == 1) { if (d > 0) p.seekToNextMediaItem() else p.seekToPreviousMediaItem(); return }
        if (d < 0 && p.currentPosition > 3000) { p.seekTo(0); return }
        p.seekTo(((p.currentMediaItemIndex + d) % n + n) % n, 0)
    }
    fun seekBy(ms: Long) { val p = player(); p.seekTo(min(max(0, p.currentPosition + ms), max(0, p.duration - 500))) }

    private fun folderEnd() {
        if (opts["rep"] != 0) return
        when (opts["end"]) {
            0 -> playFolder(cur + 1)
            2 -> { exo?.pause(); exo?.seekTo(0, 0) }
        }
    }

    fun applyOpts() {
        val p = exo ?: return
        p.shuffleModeEnabled = opts["shuf"] == 1
        p.repeatMode = when (opts["rep"]) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> if (opts["end"] == 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }
        p.setHandleAudioBecomingNoisy(opts["unplug"] == 1)
        p.pauseAtEndOfMediaItems = false
        setSleep(opts["sleep"])
    }

    /* ---------- Schlaf-Timer ---------- */
    private var sleepEnd = 0L
    private val sleepR = Runnable { exo?.pause(); opts["sleep"] = 0; sleepEnd = 0; changed() }
    private var sleepSet = -1
    private fun setSleep(i: Int) {
        if (i == sleepSet) return
        sleepSet = i; main.removeCallbacks(sleepR); sleepEnd = 0
        val min = intArrayOf(0, 15, 30, 60, 90)[i.coerceIn(0, 4)]
        if (min > 0) { sleepEnd = System.currentTimeMillis() + min * 60_000L; main.postDelayed(sleepR, min * 60_000L) }
    }
    fun sleepLeftMin() = if (sleepEnd == 0L) 0 else ((sleepEnd - System.currentTimeMillis()) / 60_000L + 1).toInt()

    /* ---------- Ein-/Ausblenden + Zustand sichern ---------- */
    private var saveCnt = 0
    private val tickR = object : Runnable {
        override fun run() {
            exo?.let { p ->
                val xf = intArrayOf(0, 2000, 5000, 8000)[opts["xf"].coerceIn(0, 3)]
                p.volume = if (xf == 0 || p.duration <= 0) 1f else min(1f, min(p.currentPosition / xf.toFloat(), (p.duration - p.currentPosition) / xf.toFloat())).coerceIn(.05f, 1f)
                if (p.isPlaying && ++saveCnt % 50 == 0) saveState()
            }
            main.postDelayed(this, 100)
        }
    }

    private fun saveState() {
        val f = folders.getOrNull(cur) ?: return; val p = exo ?: return
        app.getSharedPreferences("state", 0).edit().putString("path", f.path).putInt("track", p.currentMediaItemIndex).putLong("pos", p.currentPosition).apply()
    }
    private fun restoreState() {
        if (folders.isEmpty()) return
        val sp = app.getSharedPreferences("state", 0)
        val i = folders.indexOfFirst { it.path == sp.getString("path", null) }
        if (i >= 0) playFolder(i, sp.getInt("track", 0), sp.getLong("pos", 0), opts["resume"] == 1)
        else playFolder(0, play = false)
    }

    /* ---------- Formatanzeige ---------- */
    private fun readFormat() {
        val t = track() ?: run { fmt = Triple("", "", ""); hires = false; return }
        fmt = Triple(t.ext, "", ""); hires = false
        thread(name = "fmt") {
            var bits = 0; var sr = 0; var br = 0
            try {
                val r = MediaMetadataRetriever(); r.setDataSource(app, t.uri)
                br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                if (Build.VERSION.SDK_INT >= 31) {
                    bits = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
                    sr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                }
                r.release()
            } catch (_: Exception) { }
            val ext = t.ext.uppercase()
            val lossless = ext in setOf("FLAC", "WAV", "AIF", "AIFF", "ALAC", "WV", "APE", "DSF", "DFF")
            val f = when {
                ext == "DSF" || ext == "DFF" -> Triple("DSD", "DSD64", "2.8MHZ")
                lossless -> Triple(ext, "${if (bits > 0) bits else 16}BIT", if (sr > 0) "${sr / 1000}KHZ" else "")
                else -> Triple(ext, if (br > 0) "${br / 1000}K" else "", "")
            }
            main.post { if (track() == t) { fmt = f; hires = bits >= 24 || sr > 48000 || f.first == "DSD"; changed() } }
        }
    }

    fun quit() { saveState(); exo?.release(); exo = null }
}
