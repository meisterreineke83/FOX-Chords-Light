package io.github.lightsongbook.core

/** Practice recordings: which files count as audio and which song a file belongs to. */
object Audio {

    val EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")

    fun isAudioFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Lower-case letters and digits only: "Don't Stop Believin'" -> "dontstopbelievin". */
    internal fun key(text: String): String =
        Search.normalize(text).filter { it.isLetterOrDigit() }

    /**
     * Finds the recording for [song] among [fileNames] by title: the file name has to contain
     * the song title ("Toto - Africa (live).mp3" belongs to "Africa"). If several match,
     * the one with the least extra text wins.
     */
    fun match(song: Song, fileNames: List<String>): String? {
        val title = key(song.title)
        if (title.length < 3) return null
        val artist = key(song.artist)
        return fileNames
            .filter { isAudioFile(it) }
            .map { it to key(it.substringBeforeLast('.')) }
            .filter { (_, k) -> k.contains(title) }
            .minByOrNull { (_, k) ->
                var extra = k.length - title.length
                if (artist.isNotEmpty() && k.contains(artist)) extra -= artist.length
                extra
            }
            ?.first
    }

    /** "3:07" */
    fun formatTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
