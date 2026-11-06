package de.y2k.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

class Track(val uri: Uri, val file: String, val title: String, val artist: String, val album: String, val durMs: Long, val trackNo: Int, val ext: String)
class Folder(val name: String, val path: String, val tracks: List<Track>)
/** Verzeichnisbaum aller Ordner mit Musik (für den Bash-Browser "Ordner hinzufügen") */
class Dir(val name: String, val key: String) { val kids = sortedMapOf<String, Dir>(String.CASE_INSENSITIVE_ORDER); var count = 0 }
class ScanResult(val folders: List<Folder>, val root: Dir)

/** Liest Musik über MediaStore ein; jeder Ordner wird zur Playlist. */
object Library {
    fun scan(ctx: Context, o: Opts, progress: (String, Int) -> Unit = { _, _ -> }): ScanResult {
        val root = Dir("/", "")
        val added = o.added()
        val uri = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK)
        if (Build.VERSION.SDK_INT >= 29) { cols += MediaStore.Audio.Media.RELATIVE_PATH; cols += MediaStore.Audio.Media.VOLUME_NAME }
        else cols += "_data"
        val groups = LinkedHashMap<String, MutableList<Track>>()
        ctx.contentResolver.query(uri, cols.toTypedArray(), null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlb = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iTrk = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iRel = c.getColumnIndex("relative_path"); val iVol = c.getColumnIndex("volume_name"); val iData = c.getColumnIndex("_data")
            var n = 0
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                val ext = name.substringAfterLast('.', "")
                Palette.typeOf(ext) ?: continue
                val vol = if (iVol >= 0) c.getString(iVol) ?: "external_primary" else "external_primary"
                var rel = if (iRel >= 0) c.getString(iRel) ?: "" else (c.getString(iData) ?: "").substringAfter("/0/", "").substringBeforeLast('/', "") + "/"
                // Baum für den Ordner-Browser (alle Speicher)
                var node = root.kids.getOrPut(vol) { Dir(if (vol == "external_primary") "intern" else "sd-$vol", "$vol|") }
                node.count++
                for (seg in rel.trimEnd('/').split('/').filter { it.isNotEmpty() }) { val k = node.key + seg + "/"; node = node.kids.getOrPut(seg) { Dir(seg, k) }; node.count++ }
                // Quelle: 0 = /Music intern, 1 = SD /Music, 2 = alles – plus manuell hinzugefügte Ordner
                val inMusic = rel.startsWith("Music/", ignoreCase = true)
                val addedHit = added.firstOrNull { a -> a.substringBefore('|') == vol && rel.startsWith(a.substringAfter('|')) }
                val srcOk = when (o["src"]) { 0 -> inMusic && vol == "external_primary"; 1 -> inMusic && vol != "external_primary"; else -> true }
                if (!srcOk && addedHit == null) continue
                if (o["hidden"] == 0 && rel.split('/').any { it.startsWith(".") }) continue
                if (o["sub"] == 0) {
                    val parts = rel.trimEnd('/').split('/')
                    rel = if (!srcOk && addedHit != null) addedHit.substringAfter('|') else parts.take(if (inMusic) 2 else 1).joinToString("/") + "/"
                }
                val id = c.getLong(iId)
                val base = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(vol) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val t = Track(ContentUris.withAppendedId(base, id), name,
                    c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                    c.getString(iArt)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "",
                    c.getString(iAlb) ?: "", c.getLong(iDur), c.getInt(iTrk) % 1000, ext.uppercase())
                groups.getOrPut("$vol|$rel") { ArrayList() }.add(t)
                if (++n % 50 == 0) progress(rel, n)
            }
        }
        val sort = o["sort"]
        return ScanResult(groups.map { (key, list) ->
            val rel = key.substringAfter('|').trimEnd('/')
            val nm = rel.substringAfterLast('/').ifBlank { if (rel.isBlank()) "Music" else rel }
            Folder(nm, rel, when (sort) {
                1 -> list.sortedBy { it.file.lowercase() }
                2 -> list.sortedBy { it.title.lowercase() }
                else -> list.sortedWith(compareBy<Track>({ if (it.trackNo == 0) 9999 else it.trackNo }, { it.file.lowercase() }))
            })
        }.sortedBy { it.path.lowercase() }, root)
    }
}
