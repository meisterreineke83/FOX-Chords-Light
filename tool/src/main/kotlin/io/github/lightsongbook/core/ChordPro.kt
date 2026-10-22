package io.github.lightsongbook.core

/** One fragment of lyrics with the chord (if any) that sits above its first letter. */
data class Part(val chord: String?, val text: String)

/** Parts that belong to one word, so a line only ever wraps between words. */
data class Word(val parts: List<Part>)

sealed class SongLine {
    /** A section heading such as "Chorus" or a ChordPro comment. */
    data class Section(val label: String) : SongLine()

    /** Lyrics, optionally with chords above them. */
    data class Lyrics(val words: List<Word>, val hasChords: Boolean) : SongLine()

    /**
     * Pre-formatted text that has to keep its spacing (tabs, grids, aligned chord lines).
     * If [transpose] is true, chord words in it are transposed.
     */
    data class Mono(val text: String, val transpose: Boolean) : SongLine()

    object Blank : SongLine()
}

/**
 * A small ChordPro reader covering what Songbook Pro writes:
 * inline `[chords]`, `{c: …}` comments, tab and grid blocks, and metadata directives.
 */
object ChordPro {

    // Android (ICU) needs "{", "}", "[" and "]" escaped even where plain Java accepts them.
    private val DIRECTIVE = Regex("""^\{\s*([A-Za-z_]+)\s*(?::\s*(.*?))?\s*\}\s*$""")
    private val CHORD_IN_LINE = Regex("""\[([^\]\n]*)\]""")
    private val SECTION_WORDS = Regex(
        """^(intro|outro|verse|vers|strophe|chorus|refrain|pre-?chorus|bridge|interlude|instrumental|solo|tag|ending|coda|zwischenspiel|ref\.?)(\s*\d+)?\s*:?\s*(\(.*\))?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val COMMENT_DIRECTIVES = setOf("c", "comment", "ci", "comment_italic", "cb", "comment_box", "highlight")
    private val TAB_START = setOf("sot", "start_of_tab")
    private val TAB_END = setOf("eot", "end_of_tab")
    private val GRID_START = setOf("sog", "start_of_grid")
    private val GRID_END = setOf("eog", "end_of_grid")
    private val CHORUS_START = setOf("soc", "start_of_chorus")
    private val VERSE_START = setOf("sov", "start_of_verse")
    private val BRIDGE_START = setOf("sob", "start_of_bridge")

    fun parse(content: String): List<SongLine> {
        val out = ArrayList<SongLine>()
        var block: String? = null // "tab" or "grid"
        for (rawLine in content.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = rawLine.trimEnd()
            val directive = DIRECTIVE.matchEntire(line.trim())
            if (directive != null) {
                val name = directive.groupValues[1].lowercase()
                val value = directive.groupValues[2]
                when {
                    name in TAB_START -> { block = "tab"; continue }
                    name in GRID_START -> { block = "grid"; continue }
                    name in TAB_END || name in GRID_END -> { block = null; continue }
                    name in COMMENT_DIRECTIVES -> { out += SongLine.Section(value); continue }
                    name in CHORUS_START -> { out += SongLine.Section(value.ifEmpty { "Chorus" }); continue }
                    name in VERSE_START -> { if (value.isNotEmpty()) out += SongLine.Section(value); continue }
                    name in BRIDGE_START -> { out += SongLine.Section(value.ifEmpty { "Bridge" }); continue }
                    else -> continue // metadata and unknown directives are not displayed
                }
            }
            when (block) {
                "tab" -> { out += SongLine.Mono(line, transpose = false); continue }
                "grid" -> { out += SongLine.Mono(line, transpose = true); continue }
            }
            out += parseLine(line)
        }
        // collapse runs of blank lines and trim them at both ends
        val collapsed = ArrayList<SongLine>(out.size)
        for (l in out) {
            if (l is SongLine.Blank && (collapsed.isEmpty() || collapsed.last() is SongLine.Blank)) continue
            collapsed += l
        }
        while (collapsed.lastOrNull() is SongLine.Blank) collapsed.removeAt(collapsed.lastIndex)
        return collapsed
    }

    private fun parseLine(line: String): SongLine {
        if (line.isBlank()) return SongLine.Blank
        if (line.startsWith("#")) return SongLine.Blank // ChordPro comment line

        val hasOpen = line.contains('[')
        val hasClose = line.contains(']')
        if (hasOpen && !hasClose) {
            // Old-style chord line written with stray brackets: "[F#m      [B      [E"
            return SongLine.Mono(line.replace("[", " "), transpose = true)
        }
        if (!hasOpen) {
            val trimmed = line.trim()
            if (SECTION_WORDS.matches(trimmed)) return SongLine.Section(trimmed.trimEnd(':'))
            if (Chords.isChordLine(line)) return SongLine.Mono(line, transpose = true)
            // spacing matters (beat counts, aligned notes): keep it as typed
            if (line.startsWith("  ") || line.contains("   ")) return SongLine.Mono(line, transpose = false)
            return SongLine.Lyrics(buildWords(listOf(null to line)), hasChords = false)
        }

        val segments = ArrayList<Pair<String?, String>>()
        var pos = 0
        var pendingChord: String? = null
        for (m in CHORD_IN_LINE.findAll(line)) {
            val before = line.substring(pos, m.range.first)
            if (before.isNotEmpty() || pendingChord != null) segments += pendingChord to before
            pendingChord = m.groupValues[1].trim()
            pos = m.range.last + 1
        }
        segments += pendingChord to line.substring(pos)
        val hasChords = segments.any { it.first != null }
        return SongLine.Lyrics(buildWords(segments), hasChords)
    }

    /**
     * Splits chord/text segments into words. A chord in the middle of a word stays
     * attached to that word, so wrapping never separates a chord from its syllable.
     */
    internal fun buildWords(segments: List<Pair<String?, String>>): List<Word> {
        val words = ArrayList<Word>()
        var parts = ArrayList<Part>()
        for ((chord, text) in segments) {
            var currentChord = chord
            val sb = StringBuilder()
            for (c in text) {
                val wordEnded = sb.isNotEmpty() && sb.last() == ' ' && c != ' '
                if (wordEnded) {
                    parts += Part(currentChord, sb.toString())
                    currentChord = null
                    sb.setLength(0)
                    words += Word(parts)
                    parts = ArrayList()
                }
                sb.append(c)
            }
            if (sb.isNotEmpty() || currentChord != null) parts += Part(currentChord, sb.toString())
            if (sb.isNotEmpty() && sb.last() == ' ') {
                words += Word(parts)
                parts = ArrayList()
            }
        }
        if (parts.isNotEmpty()) words += Word(parts)
        return words
    }

    /** Song text without chords and directives, for searching. */
    fun plainLyrics(content: String): String =
        content.lineSequence()
            .filterNot { it.trim().startsWith("{") }
            .joinToString("\n") { CHORD_IN_LINE.replace(it, "") }

    fun chordTokens(content: String): Sequence<String> =
        CHORD_IN_LINE.findAll(content).map { it.groupValues[1].trim() }

    fun usesGermanNotation(content: String): Boolean =
        chordTokens(content).any { token ->
            token.split(' ').any { w -> Chords.parseChord(w)?.usesGermanH == true }
        }

    /** First chord of the song, parsed. */
    private fun firstChord(content: String): Chords.ParsedChord? {
        val german = usesGermanNotation(content)
        for (token in chordTokens(content)) {
            for (w in token.split(' ')) {
                Chords.parseChord(w, german)?.let { return it }
            }
        }
        return null
    }

    /** Major key guessed from the first chord (a minor first chord means its relative major). */
    fun guessKey(content: String): Int? {
        val first = firstChord(content) ?: return null
        return if (first.isMinor) Chords.mod12(first.root + 3) else first.root
    }

    /** True if the song most likely is in the relative minor of [majorKey]. */
    fun looksMinor(content: String, majorKey: Int): Boolean {
        val first = firstChord(content) ?: return false
        return first.isMinor && first.root == Chords.mod12(majorKey - 3)
    }

    data class FileMetadata(
        val title: String?,
        val artist: String?,
        val keyPitch: Int?,
        val capo: Int,
        val tempo: Int,
        val body: String,
    )

    /** Reads a standalone .cho/.pro file: metadata directives are pulled out of the body. */
    fun readFile(text: String): FileMetadata {
        var title: String? = null
        var artist: String? = null
        var key: Int? = null
        var capo = 0
        var tempo = 0
        val body = StringBuilder()
        for (line in text.removePrefix("﻿").replace("\r\n", "\n").split('\n')) {
            val m = DIRECTIVE.matchEntire(line.trim())
            if (m != null) {
                val value = m.groupValues[2].trim()
                when (m.groupValues[1].lowercase()) {
                    "title", "t" -> { title = value; continue }
                    "artist", "subtitle", "st", "su" -> { if (artist == null) artist = value; continue }
                    "key" -> {
                        Chords.parseChord(value)?.let { c ->
                            key = if (c.isMinor) Chords.mod12(c.root + 3) else c.root
                        }
                        continue
                    }
                    "capo" -> { capo = value.toIntOrNull() ?: 0; continue }
                    "tempo" -> { tempo = value.toIntOrNull() ?: 0; continue }
                }
            }
            body.append(line).append('\n')
        }
        return FileMetadata(title, artist, key, capo, tempo, body.toString().trim('\n'))
    }
}
