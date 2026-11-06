package de.y2k.player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import java.io.File

/** Systemdaten im neofetch-Stil für das "Über"-Fenster. */
object SysInfo {
    /** App-Icon als Pixel-Logo mit Android-Hut (g = Android-Grün, e = Augen). Feste Farben, gehört zum Logo. */
    val LOGO = listOf(
        ".....g..........g.....", "......g........g......", ".......gggggggg.......", ".....gg........gg.....",
        "....g...g....g...g....", "....gggggggggggggg....", "......................", ".SSSSSSSSSSSSSSSSSSSS.",
        ".S..................S.", ".S.L...L...L...L..R.S.", ".S.L...L...L...L..R.S.", ".S.....L...L...L..R.S.",
        ".S.........L...L..R.S.", ".S.............L..R.S.", ".S..................S.", ".SSSSSSSSSSSSSSSSSSSS.",
        "......................", "...RR..KKKKKKKKKK.....", "..RR..................")
    val LOGO_COLORS = mapOf('g' to 0x3DDC84, 'S' to 0xD9DFE6, 'D' to 0x05080A, 'L' to 0xC39BFF, 'R' to 0xFF4433, 'K' to 0x15181B)

    /** Liste aus (Bezeichnung, Wert); Bezeichnung leer = Überschrift */
    fun lines(ctx: Context): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        // echter Gerätename aus den Android-Einstellungen, sonst Modell
        val host = (android.provider.Settings.Global.getString(ctx.contentResolver, "device_name") ?: Build.MODEL)
            .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "android" }
        out += "" to "p-hr-x1@$host"
        out += "" to "-------------------"
        out += "OS" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        out += L("Gerät", "Device") to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".replace("_", " ")
        out += "Kernel" to (System.getProperty("os.version") ?: "?").take(24)
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
        val ghz = readMaxFreq()?.let { " · %.1f GHz".format(it / 1_000_000f) } ?: ""
        out += "CPU" to "$soc · ${Runtime.getRuntime().availableProcessors()}x$ghz".replace("x · ", "x ")
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        out += "RAM" to "%.1f / %.1f GB".format((mi.totalMem - mi.availMem) / 1e9, mi.totalMem / 1e9)
        try { val st = StatFs(Environment.getExternalStorageDirectory().path)
            out += L("Speicher", "Storage") to "%.0f / %.0f GB".format((st.totalBytes - st.availableBytes) / 1e9, st.totalBytes / 1e9) } catch (_: Exception) { }
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bat != null) {
            val lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val st = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            out += L("Akku", "Battery") to "$lvl %" + (if (st == BatteryManager.BATTERY_STATUS_CHARGING) L(" (lädt)", " (charging)") else "") + " · " + "%.0f".format(temp) + " °C"
        }
        val up = SystemClock.elapsedRealtime() / 60000
        out += "Uptime" to (if (up >= 60) "${up / 60} h ${up % 60} min" else "$up min")
        @Suppress("DEPRECATION") val d = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        out += "Display" to "${m.widthPixels}x${m.heightPixels} · " + "%.0f".format(d.refreshRate) + " Hz"
        val au = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sr = au.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        out += "Audio" to "${if (sr != null) "${sr / 1000} kHz" else "?"} · Vol ${au.getStreamVolume(AudioManager.STREAM_MUSIC)}/${au.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
        out += "Player" to "P-HR-X1 ${appVersion(ctx)}"
        out += "Code" to "vibecoded with Claude"
        out += L("Musik", "Music") to "${Engine.folders.size} " + L("Ordner", "folders") + " · ${Engine.folders.sumOf { it.tracks.size }} " + L("Lieder", "tracks")
        out += L("Schema", "Scheme") to Palette.SCHEMES.getOrNull(Engine.opts["scheme"]).let { if (it == null || it.size < 4) "${Palette.COLORS[Engine.opts["color"]].name} / ${Palette.COLORS.getOrNull(Engine.opts["col2"])?.name ?: "–"}" else L(it[0], it[1]) }
        return out
    }

    private fun readMaxFreq(): Long? = try {
        (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() }.maxOrNull()
    } catch (_: Exception) { null }

    private fun appVersion(ctx: Context) = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
}
