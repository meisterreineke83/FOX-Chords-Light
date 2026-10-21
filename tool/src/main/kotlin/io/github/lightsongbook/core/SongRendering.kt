package io.github.lightsongbook.core

/**
 * Applies a [Transposition] to parsed song lines. Kept separate from the UI so it can be tested.
 */
object SongRendering {

    sealed class Block {
        data class Line(val line: SongLine) : Block()

        /** Consecutive pre-formatted lines, shown together so they scroll sideways as one. */
        data class Mono(val lines: List<String>) : Block()
    }

    fun blocks(lines: List<SongLine>): List<Block> {
        val out = ArrayList<Block>()
        val mono = ArrayList<String>()
        fun flush() {
            if (mono.isNotEmpty()) {
                out += Block.Mono(mono.toList())
                mono.clear()
            }
        }
        for (l in lines) {
            if (l is SongLine.Mono) {
                mono += l.text
            } else {
                flush()
                out += Block.Line(l)
            }
        }
        flush()
        return out
    }

    fun transpose(lines: List<SongLine>, t: Transposition): List<SongLine> {
        if (Chords.mod12(t.chordShift) == 0) return lines
        return lines.map { line ->
            when (line) {
                is SongLine.Lyrics -> if (!line.hasChords) line else line.copy(
                    words = line.words.map { w ->
                        Word(w.parts.map { p -> if (p.chord == null) p else p.copy(chord = t.chord(p.chord)) })
                    },
                )
                is SongLine.Mono -> if (line.transpose) line.copy(text = transposeAligned(line.text, t)) else line
                else -> line
            }
        }
    }

    /**
     * Transposes chord words in a pre-formatted line while keeping later chords in their
     * columns where possible (a longer chord name eats into the following spaces).
     */
    fun transposeAligned(text: String, t: Transposition): String {
        val sb = StringBuilder(text.length + 8)
        var debt = 0 // how many characters we are ahead of the original layout
        var i = 0
        while (i < text.length) {
            var j = i
            if (text[i] == ' ') {
                while (j < text.length && text[j] == ' ') j++
                val run = j - i
                val keep = if (debt > 0 && sb.isNotEmpty()) maxOf(1, run - debt) else run
                debt -= run - keep
                repeat(keep) { sb.append(' ') }
            } else {
                while (j < text.length && text[j] != ' ') j++
                val word = text.substring(i, j)
                val replaced = t.chord(word)
                sb.append(replaced)
                debt += replaced.length - word.length
                if (debt < 0) {
                    repeat(-debt) { sb.append(' ') }
                    debt = 0
                }
            }
            i = j
        }
        return sb.toString().trimEnd()
    }

    /** "C · Capo 2 (Griffe A) · 120 BPM" */
    fun infoLine(song: Song, t: Transposition, capo: Int): String {
        val parts = ArrayList<String>()
        t.soundingKey?.let { parts += "Ton $it" }
        if (capo > 0) parts += if (t.shapeKey != null) "Capo $capo (${t.shapeKey})" else "Capo $capo"
        if (song.tempo > 0) parts += "${song.tempo} BPM"
        return parts.joinToString(" · ")
    }
}
