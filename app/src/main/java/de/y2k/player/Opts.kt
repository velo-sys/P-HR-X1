package de.y2k.player

import android.content.Context

/** Sprache: 0 = English (Standard), 1 = Deutsch */
object Lang {
    val en: Boolean get() = !Engine.isReady() || Engine.opts["lang"] == 0
}
/** Text in beiden Sprachen */
fun L(de: String, en: String) = if (Lang.en) en else de

/** Anzeigefarben: Name (de/en), Farbe, Glühfarbe */
class DisplayColor(val de: String, val en: String, val c: Int, val glow: Int) { val name get() = if (Lang.en) en else de }

object Palette {
    val COLORS = listOf(
        Triple("Violett", "Violet", "#e2bcff" to "#aa33ff"), Triple("Eisblau", "Ice Blue", "#bff4ff" to "#3399ff"),
        Triple("Babyblau", "Baby Blue", "#d2e6ff" to "#6aa8ff"), Triple("Himmelblau", "Sky Blue", "#a3d2ff" to "#2a8cff"),
        Triple("Türkisblau", "Turquoise", "#86e4ff" to "#08a0d8"), Triple("Azur", "Azure", "#86b8ff" to "#1f6bff"),
        Triple("Stahlblau", "Steel Blue", "#b0c8e0" to "#4a7bb0"), Triple("Kobalt", "Cobalt", "#97a6ff" to "#2b45ff"),
        Triple("Saphir", "Sapphire", "#7d93ff" to "#1030e0"), Triple("Cyan", "Cyan", "#8ff7ff" to "#00ccdd"),
        Triple("Mint", "Mint", "#a8ffd8" to "#11cc99"), Triple("Grün", "Green", "#9dffb0" to "#22dd66"),
        Triple("Limette", "Lime", "#e4ff8a" to "#99cc00"), Triple("Gelb", "Yellow", "#fff38a" to "#ddbb00"),
        Triple("Gold", "Gold", "#ffd98a" to "#cc9900"), Triple("Bernstein", "Amber", "#ffb44a" to "#ff8800"),
        Triple("Orange", "Orange", "#ff9a5a" to "#ff5500"), Triple("Rot", "Red", "#ff8070" to "#ff2222"),
        Triple("Pink", "Pink", "#ff9ad5" to "#ff22aa"), Triple("Magenta", "Magenta", "#ff8cf5" to "#dd00cc"),
        Triple("Lavendel", "Lavender", "#c8c4ff" to "#6666ff"), Triple("Weiß", "White", "#f6f1e6" to "#ffcc99"),
        Triple("Lavendel knallig", "Vivid Lavender", "#c39bff" to "#8a3cff"), Triple("Knallrot", "Vivid Red", "#ff4433" to "#ff1500"),
        Triple("Regenbogen", "Rainbow", "#ff8cf5" to "#dd00cc"),
    ).map { (de, en, p) -> DisplayColor(de, en, Col.parse(p.first), Col.parse(p.second)) }
    val RAINBOW = COLORS.size - 1
    fun idx(deName: String) = COLORS.indexOfFirst { it.de == deName }
    /** Farbschemata: Name de, Name en, Hauptfarbe, Zweitfarbe */
    val SCHEMES = listOf(
        listOf("Eigenes", "Custom"), listOf("Lavendel / Rot", "Lavender / Red", "Lavendel knallig", "Knallrot"),
        listOf("Rot / Lavendel", "Red / Lavender", "Knallrot", "Lavendel knallig"), listOf("Violett / Cyan", "Violet / Cyan", "Violett", "Cyan"),
        listOf("Cyan / Pink", "Cyan / Pink", "Cyan", "Pink"), listOf("Eisblau / Saphir", "Ice / Sapphire", "Eisblau", "Saphir"),
        listOf("Bernstein / Orange", "Amber / Orange", "Bernstein", "Orange"), listOf("Mint / Lavendel", "Mint / Lavender", "Mint", "Lavendel"),
        listOf("Gold / Kobalt", "Gold / Cobalt", "Gold", "Kobalt"), listOf("Magenta / Türkis", "Magenta / Turquoise", "Magenta", "Türkisblau"),
    )
    fun typeOf(ext: String) = when (ext.lowercase()) {
        "mp3" -> "mp3"; "flac", "fla" -> "flac"; "wav", "wave" -> "wav"; "aif", "aiff", "aifc" -> "aiff"
        "m4a", "aac", "mp4", "m4b" -> "m4a"; "alac" -> "alac"; "ogg", "oga" -> "ogg"; "opus" -> "opus"
        "dsf", "dff" -> "dsd"; "wv" -> "wv"; "ape" -> "ape"; "wma" -> "wma"; else -> null
    }
}

enum class T { POT, ROLL, ACT, INFO }

/** Menüeintrag mit Texten in beiden Sprachen; Werte entweder fest (de/en) oder dynamisch. */
class Item(
    val k: String, private val de: String, private val en: String, val t: T,
    private val subDe: String? = null, private val subEn: String? = null,
    private val valDe: List<String> = emptyList(), private val valEn: List<String>? = null,
    private val dyn: (() -> List<String>)? = null, val colored: Boolean = false, val action: String? = null,
) {
    val label get() = if (Lang.en) en else de
    val sub get() = if (Lang.en) subEn ?: subDe else subDe
    val values: List<String> get() = dyn?.invoke() ?: if (Lang.en) valEn ?: valDe else valDe
}
class Cat(private val de: String, private val en: String, val icon: String, val items: List<Item>) { val name get() = if (Lang.en) en else de }

object Menu {
    val CATS: List<Cat> by lazy {
        listOf(
            Cat("Wiedergabe", "Playback", "▶", listOf(
                Item("shuf", "Zufall", "Shuffle", T.POT, "Taste X", "button X"),
                Item("rep", "Wiederholen", "Repeat", T.ROLL, "Taste Y", "button Y", listOf("Aus", "Lied", "Ordner"), listOf("Off", "Track", "Folder")),
                Item("end", "Am Ordnerende", "At folder end", T.ROLL, valDe = listOf("Nächster Ordner", "Von vorn", "Stopp"), valEn = listOf("Next folder", "Restart", "Stop")),
                Item("sleep", "Schlaf-Timer", "Sleep timer", T.ROLL, valDe = listOf("Aus", "15 min", "30 min", "60 min", "90 min"), valEn = listOf("Off", "15 min", "30 min", "60 min", "90 min")),
                Item("xf", "Ein-/Ausblenden", "Fade in/out", T.ROLL, valDe = listOf("Aus", "2 s", "5 s", "8 s"), valEn = listOf("Off", "2 s", "5 s", "8 s")),
                Item("gap", "Lückenlos", "Gapless", T.POT),
                Item("resume", "Autoplay beim Start", "Autoplay on start", T.POT, "sonst Pause an letzter Stelle", "otherwise paused at last spot"),
                Item("unplug", "Pause: Kopfhörer raus", "Pause on unplug", T.POT))),
            Cat("Bibliothek", "Library", "♫", listOf(
                Item("src", "Musikordner", "Music folder", T.ROLL, "wo gesucht wird", "where to look", listOf("/Music", "SD /Music", "Alle Speicher"), listOf("/Music", "SD /Music", "All storage")),
                Item("addfolder", "Ordner hinzufügen", "Add folder", T.ACT, action = "browse"),
                Item("sub", "Unterordner einzeln", "Subfolders separate", T.POT, "eigene Walzenplätze", "own drum slots"),
                Item("sort", "Liedreihenfolge", "Track order", T.ROLL, valDe = listOf("Titel-Nr.", "Dateiname", "Titel A–Z"), valEn = listOf("Track no.", "File name", "Title A–Z")),
                Item("hidden", "Versteckte Ordner", "Hidden folders", T.POT),
                Item("scan", "Neu einlesen", "Rescan", T.ACT, action = "scan"))),
            Cat("Visualizer", "Visualizer", "✦", listOf(
                Item("viz", "Visualizer", "Visualizer", T.ROLL, "klein + Vollbild · Tasten L / R", "small + full screen · L / R", dyn = { Viz.labels() }),
                Item("vizsm", "Glättung", "Smoothing", T.ROLL, valDe = listOf("Weich", "Normal", "Zackig"), valEn = listOf("Soft", "Normal", "Snappy")),
                Item("vizvol", "Folgt der Lautstärke", "Follows volume", T.POT, "volle Wucht ab 50 %", "full power from 50 %"),
                Item("vizinfo", "Infozeile im Vollbild", "Info line (full screen)", T.POT),
                Item("vizshow", "Vollbild zeigen", "Show full screen", T.ACT, "Taste SELECT", "button SELECT", action = "viz"))),
            Cat("Farben", "Colors", "◐", listOf(
                Item("scheme", "Farbschema", "Color scheme", T.ROLL, "Haupt / Zweit", "main / second", dyn = { Palette.SCHEMES.map { if (Lang.en) it[1] else it[0] } }),
                Item("color", "Hauptfarbe", "Main color", T.ROLL, dyn = { Palette.COLORS.map { it.name } }, colored = true),
                Item("col2", "Zweitfarbe", "Second color", T.ROLL, dyn = { Palette.COLORS.dropLast(1).map { it.name } + L("Einfarbig", "Single color") }, colored = true),
                Item("col2m", "Zweifarbig-Stil", "Two-tone style", T.ROLL, valDe = listOf("Verlauf", "Im Beat tauschen"), valEn = listOf("Gradient", "Swap on beat")),
                Item("peak", "Spitzenfarbe", "Peak color", T.ROLL, valDe = listOf("Zweitfarbe", "Zweitfarbe hell", "Hauptfarbe hell"), valEn = listOf("Second", "Second light", "Main light")))),
            Cat("Anzeige", "Display", "▣", listOf(
                Item("bright", "Helligkeit", "Brightness", T.ROLL, valDe = listOf("100 %", "75 %", "50 %", "25 %")),
                Item("glow", "Leuchten", "Glow", T.ROLL, valDe = listOf("Stark", "Normal", "Aus"), valEn = listOf("Strong", "Normal", "Off")),
                Item("scanl", "Scanlines", "Scanlines", T.POT),
                Item("flick", "Flackern", "Flicker", T.POT, "wie alte Röhre", "like an old tube"),
                Item("fmt", "Formatanzeige", "Format display", T.POT, "MP3 · FLAC · HI-RES"),
                Item("off", "Bildschirm an", "Keep screen on", T.ROLL, valDe = listOf("Immer", "30 s", "1 min", "5 min"), valEn = listOf("Always", "30 s", "1 min", "5 min")))),
            Cat("Bewegung", "Motion", "≋", listOf(
                Item("mtext", "Laufschrift zeigt", "Marquee shows", T.ROLL, valDe = listOf("Künstler – Titel", "+ Album", "+ Ordner"), valEn = listOf("Artist – Title", "+ Album", "+ Folder")),
                Item("mstyle", "Laufschrift-Stil", "Marquee style", T.ROLL, valDe = listOf("Durchlauf", "Ping-Pong", "Schreibmaschine", "Welle"), valEn = listOf("Scroll", "Ping-pong", "Typewriter", "Wave")),
                Item("mspeed", "Laufschrift-Tempo", "Marquee speed", T.ROLL, valDe = listOf("Langsam", "Normal", "Schnell"), valEn = listOf("Slow", "Normal", "Fast")),
                Item("drum", "Walze", "Drum", T.ROLL, valDe = listOf("Federnd", "Weich", "Mechanisch", "Sofort"), valEn = listOf("Springy", "Smooth", "Mechanical", "Instant")),
                Item("trans", "Fensterübergang", "Window transition", T.ROLL, valDe = listOf("Schieben", "Blende", "Scanline"), valEn = listOf("Slide", "Fade", "Scanline")),
                Item("boot", "Startanimation", "Boot animation", T.ROLL, valDe = listOf("VFD-Test", "Aus"), valEn = listOf("VFD test", "Off")),
                Item("bootnow", "Startanimation zeigen", "Show boot animation", T.ACT, action = "boot"))),
            Cat("Formate", "Formats", "◆", listOf(
                Item("fi1", "Es werden immer alle Dateien gefunden", "All files are always found", T.INFO, "spielbare Formate:", "playable formats:"),
                Item("fi2", "MP3 · FLAC · WAV", "MP3 · FLAC · WAV", T.INFO, "klassisch / verlustfrei", "classic / lossless"),
                Item("fi3", "M4A / AAC · OGG · Opus", "M4A / AAC · OGG · Opus", T.INFO, "komprimiert", "compressed"),
                Item("fi4", "AIFF · ALAC · DSD · WV · APE · WMA", "AIFF · ALAC · DSD · WV · APE · WMA", T.INFO, "folgt (Decoder) – bis dahin übersprungen", "coming (decoder) – skipped for now"))),
            Cat("System", "System", "⚙", listOf(
                Item("lang", "Sprache / Language", "Language / Sprache", T.ROLL, valDe = listOf("English", "Deutsch")),
                Item("touch", "Touch-Bedienung", "Touch control", T.POT, "Tippen & Wischen", "tap & swipe"),
                Item("swapab", "A/B tauschen", "Swap A/B", T.POT),
                Item("vosd", "Lautstärke-Anzeige", "Volume display", T.POT),
                Item("keys", "Tastenbelegung", "Button map", T.ACT, action = "keys"),
                Item("keytest", "Tastentest", "Button test", T.ACT, action = "keytest"),
                Item("about", "Über", "About", T.ACT, action = "about"),
                Item("reset", "Werkseinstellungen", "Factory reset", T.ACT, action = "reset"),
                Item("quit", "Beenden", "Quit", T.ACT, "Musik stoppen", "stop music", action = "quit"))),
        )
    }
}

/** Alle Einstellungen als Ints, gespeichert in SharedPreferences. */
class Opts(ctx: Context) {
    private val sp = ctx.getSharedPreferences("opts", Context.MODE_PRIVATE)
    val defaults: Map<String, Int> = mapOf("src" to 0, "sub" to 1, "sort" to 0, "hidden" to 0, "shuf" to 0, "rep" to 0, "end" to 0, "gap" to 1, "xf" to 0,
        "resume" to 0, "unplug" to 1, "sleep" to 0, "scheme" to 1, "color" to Palette.idx("Lavendel knallig"),
        "col2" to Palette.idx("Knallrot"), "col2m" to 0, "peak" to 1, "bright" to 0, "glow" to 1, "scanl" to 1, "fmt" to 1,
        "mtext" to 0, "off" to 0, "mstyle" to 0, "mspeed" to 1, "drum" to 0, "trans" to 0, "flick" to 0, "boot" to 0,
        "viz" to Viz.NAMES.indexOf("Delfine").coerceAtLeast(0), "vizsm" to 1, "vizvol" to 1, "vizinfo" to 1, "vosd" to 1, "swapab" to 0,
        "touch" to 1, "lang" to 0)
    private val v = HashMap<String, Int>()
    init { defaults.forEach { (k, d) -> v[k] = sp.getInt(k, d) } }
    operator fun get(k: String) = v[k] ?: 0
    operator fun set(k: String, value: Int) { v[k] = value; sp.edit().putInt(k, value).apply() }
    fun added(): Set<String> = sp.getStringSet("added", emptySet())!!.toSet()
    fun toggleAdded(key: String): Boolean {
        val cur = added().toMutableSet(); val on = !cur.remove(key); if (on) cur += key
        sp.edit().putStringSet("added", cur).apply(); return on
    }
    fun reset() { val e = sp.edit(); defaults.forEach { (k, d) -> if (k != "lang") { v[k] = d; e.putInt(k, d) } }; e.apply() }

    /** Nach Änderung: Farbschema <-> Einzelfarben abgleichen */
    fun hook(k: String) {
        if (k == "scheme" && this["scheme"] > 0) {
            val s = Palette.SCHEMES[this["scheme"]]; this["color"] = Palette.idx(s[2]); this["col2"] = Palette.idx(s[3])
        }
        if (k == "color" || k == "col2") this["scheme"] = 0
    }
}
