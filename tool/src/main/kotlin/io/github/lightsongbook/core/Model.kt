package io.github.lightsongbook.core

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * @param keyPitch major key of the song as written (C = 0), or null if unknown.
 * @param keyShift transposition the user chose in Songbook Pro (semitones).
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val keyPitch: Int?,
    val keyShift: Int,
    val capo: Int,
    val content: String,
    val tempo: Int = 0,
    val durationSeconds: Int = 0,
    val notes: String = "",
    val source: Source = Source.Backup,
) {
    enum class Source { Backup, ChordPro }

    /** True if the chart uses German chord names (H instead of B). */
    val usesGermanNotation: Boolean by lazy { ChordPro.usesGermanNotation(content) }

    internal val searchText: String by lazy { Search.normalize("$title\n$artist") }
    internal val searchLyrics: String by lazy { Search.normalize(ChordPro.plainLyrics(content)) }
}

data class SetEntry(
    val songId: String,
    /** extra transposition for this song in this set, on top of the song's own shift */
    val keyOffset: Int,
    /** capo for this set entry; 0 means "use the song's capo" */
    val capo: Int,
)

data class SetList(
    val id: String,
    val name: String,
    val date: String,
    val entries: List<SetEntry>,
    /** true for setlists made on the phone; backup setlists are read-only */
    val editable: Boolean = false,
)

data class Library(
    val songs: List<Song>,
    val sets: List<SetList>,
    /** songs the user removed on the phone (kept so they can be restored) */
    val hiddenSongs: List<Song> = emptyList(),
) {
    private val byId: Map<String, Song> by lazy { songs.associateBy { it.id } }

    fun song(id: String): Song? = byId[id]

    internal val songIds: Set<String> by lazy { byId.keys }

    fun set(id: String): SetList? = sets.firstOrNull { it.id == id }

    /** Songs sorted alphabetically by title (locale-aware). */
    val sortedSongs: List<Song> by lazy {
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
        songs.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    /** Title/artist matches first, then songs whose lyrics contain the query. */
    fun search(query: String): List<Song> {
        val q = Search.normalize(query).trim()
        if (q.isEmpty()) return sortedSongs
        val terms = q.split(Regex("\\s+"))
        val titleHits = sortedSongs.filter { s -> terms.all { s.searchText.contains(it) } }
        val hitIds = titleHits.mapTo(HashSet()) { it.id }
        val lyricHits = sortedSongs.filter { s ->
            s.id !in hitIds && terms.all { s.searchLyrics.contains(it) || s.searchText.contains(it) }
        }
        return titleHits + lyricHits
    }

    companion object {
        val EMPTY = Library(emptyList(), emptyList())
    }
}

internal object Search {
    private val MARKS = Regex("\\p{Mn}+")
    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
            .replace("ß", "ss")
}

/**
 * What a song looks like after applying a transposition and capo:
 * which semitone shift to apply to the written chords, and how to spell them.
 */
data class Transposition(
    /** semitones to add to the written chords */
    val chordShift: Int,
    val flats: Boolean,
    val german: Boolean,
    /** sounding key, e.g. "C" or "Am"; null if unknown */
    val soundingKey: String?,
    /** key of the chord shapes when a capo is used, e.g. "A"; null without capo */
    val shapeKey: String?,
) {
    fun chord(token: String): String = Chords.transposeToken(token, chordShift, flats, german)

    companion object {
        /**
         * @param totalShift song shift + any set offset + user adjustment
         */
        fun of(song: Song, totalShift: Int, capo: Int): Transposition {
            val german = song.usesGermanNotation
            val writtenKey = song.keyPitch ?: ChordPro.guessKey(song.content)
            val minor = writtenKey != null && ChordPro.looksMinor(song.content, writtenKey)
            val chordShift = totalShift - capo
            if (writtenKey == null) {
                return Transposition(chordShift, flats = false, german = german, soundingKey = null, shapeKey = null)
            }
            val sounding = Chords.mod12(writtenKey + totalShift)
            val shapes = Chords.mod12(sounding - capo)
            val flats = Chords.prefersFlats(shapes)
            fun name(majorPitch: Int, useFlats: Boolean): String =
                if (minor) Chords.pitchName(majorPitch - 3, useFlats, german) + "m"
                else Chords.pitchName(majorPitch, useFlats, german)
            return Transposition(
                chordShift = chordShift,
                flats = flats,
                german = german,
                soundingKey = name(sounding, Chords.prefersFlats(sounding)),
                shapeKey = if (capo > 0) name(shapes, flats) else null,
            )
        }
    }
}
