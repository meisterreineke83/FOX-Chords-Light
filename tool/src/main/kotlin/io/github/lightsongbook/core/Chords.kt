package io.github.lightsongbook.core

/**
 * Note and chord helpers. Pitch classes are C-based (C = 0, C# = 1, … B = 11).
 */
object Chords {

    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** Major keys that are conventionally written with flats: F, Bb, Eb, Ab, Db. */
    private val FLAT_KEYS = setOf(5, 10, 3, 8, 1)

    private val BASE_PITCH = mapOf(
        'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11, 'H' to 11,
    )

    /**
     * A single chord word such as `F#m7`, `Bb/D`, `Hm` or `Gsus4`.
     * The suffix may contain the usual quality / extension characters.
     */
    private val CHORD_WORD = Regex(
        """^([A-H])(#|b)?((?:maj|min|m|M|dim|aug|sus|add|[0-9]|[+\-#b()°ø])*)(?:/([A-H])(#|b)?)?$"""
    )

    fun mod12(value: Int): Int = ((value % 12) + 12) % 12

    /** Songbook Pro stores keys as an index starting at A (A = 0, A# = 1, … G# = 11). */
    fun pitchFromSongbookKey(sbpKey: Int): Int = mod12(sbpKey + 9)

    fun pitchName(pitch: Int, flats: Boolean, german: Boolean = false): String {
        val p = mod12(pitch)
        if (german) {
            if (p == 11) return "H"
            if (p == 10 && flats) return "B"
        }
        return (if (flats) FLAT_NAMES else SHARP_NAMES)[p]
    }

    fun prefersFlats(majorKeyPitch: Int): Boolean = mod12(majorKeyPitch) in FLAT_KEYS

    /** Parses a root like `F#`, `Bb` or `H` into a pitch class, or null. */
    fun parseRoot(text: String): Int? {
        if (text.isEmpty()) return null
        val base = BASE_PITCH[text[0]] ?: return null
        val accidental = text.getOrNull(1)
        return when (accidental) {
            '#' -> mod12(base + 1)
            'b' -> mod12(base - 1)
            else -> base
        }
    }

    private fun parseNote(letter: String, accidental: String, german: Boolean): Int? {
        if (german && letter == "B" && accidental.isEmpty()) return 10
        return parseRoot(letter + accidental)
    }

    data class ParsedChord(val root: Int, val suffix: String, val bass: Int?, val usesGermanH: Boolean) {
        val isMinor: Boolean
            get() = suffix.startsWith("m") && !suffix.startsWith("maj")
    }

    /**
     * @param german when true, a plain "B" is read as Bb (German notation, where "H" is B natural).
     */
    fun parseChord(word: String, german: Boolean = false): ParsedChord? {
        val match = CHORD_WORD.matchEntire(word) ?: return null
        val (rootLetter, rootAcc, suffix, bassLetter, bassAcc) = match.destructured
        // "Hb" is not a thing in German notation.
        if (rootLetter == "H" && rootAcc == "b") return null
        val root = parseNote(rootLetter, rootAcc, german) ?: return null
        val bass = if (bassLetter.isNotEmpty()) parseNote(bassLetter, bassAcc, german) else null
        return ParsedChord(root, suffix, bass, rootLetter == "H" || bassLetter == "H")
    }

    /**
     * Transposes everything inside a chord bracket. The token may contain several
     * words (`D /`, `Am |`, `Gsus2 /`); words that are not chords are kept as they are.
     * A shift of 0 returns the token untouched so original spellings survive.
     */
    fun transposeToken(token: String, semitones: Int, flats: Boolean, german: Boolean = false): String {
        if (mod12(semitones) == 0) return token
        val sb = StringBuilder(token.length + 4)
        var i = 0
        while (i < token.length) {
            if (token[i].isWhitespace()) {
                sb.append(token[i]); i++; continue
            }
            var j = i
            while (j < token.length && !token[j].isWhitespace()) j++
            sb.append(transposeWord(token.substring(i, j), semitones, flats, german))
            i = j
        }
        return sb.toString()
    }

    fun transposeWord(word: String, semitones: Int, flats: Boolean, german: Boolean = false): String {
        // Allow trailing punctuation such as "G," or "(Am)".
        val leading = word.takeWhile { it == '(' }
        val trailing = word.drop(leading.length).takeLastWhile { it == ')' || it == ',' || it == '.' }
        val core = word.substring(leading.length, word.length - trailing.length)
        val chord = parseChord(core, german) ?: return word
        val root = pitchName(chord.root + semitones, flats, german)
        val bass = chord.bass?.let { "/" + pitchName(it + semitones, flats, german) } ?: ""
        return leading + root + chord.suffix + bass + trailing
    }

    /** True if every word of the line is a chord or a bar / repeat marker. */
    fun isChordLine(line: String): Boolean {
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        var chords = 0
        for (w in words) {
            when {
                parseChord(w.trim('(', ')')) != null -> chords++
                w.all { it == '|' || it == '/' || it == ':' || it == '.' || it == '-' || it == '%' } -> Unit
                Regex("""^\(?x\d+\)?$""", RegexOption.IGNORE_CASE).matches(w) -> Unit
                else -> return false
            }
        }
        return chords > 0
    }
}
