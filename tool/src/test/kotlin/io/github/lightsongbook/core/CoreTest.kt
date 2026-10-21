package io.github.lightsongbook.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordsTest {
    @Test
    fun transposesSimpleChords() {
        assertEquals("C", Chords.transposeToken("E", 8, flats = false))
        assertEquals("F#m7", Chords.transposeToken("Em7", 2, flats = false))
        assertEquals("Bb/D", Chords.transposeToken("A/C#", 1, flats = true))
        assertEquals("Gsus4", Chords.transposeToken("Gsus4", 0, flats = true))
    }

    @Test
    fun keepsNonChordWords() {
        assertEquals("E /", Chords.transposeToken("D /", 2, flats = false))
        assertEquals("N.C.", Chords.transposeToken("N.C.", 3, flats = false))
        assertEquals("*d", Chords.transposeToken("*d", 3, flats = false))
        assertEquals("|", Chords.transposeToken("|", 3, flats = false))
        assertEquals("fade out", Chords.transposeToken("fade out", 3, flats = false))
    }

    @Test
    fun handlesGermanNotation() {
        assertEquals("C#m", Chords.transposeToken("Hm", 2, flats = false))
        assertEquals("Hm", Chords.transposeToken("Am", 2, flats = false, german = true))
        assertEquals("B", Chords.transposeToken("A", 1, flats = true, german = true))
        assertEquals("C", Chords.transposeToken("B", 2, flats = false, german = true)) // German B = Bb
    }

    @Test
    fun songbookKeyIndexStartsAtA() {
        assertEquals(9, Chords.pitchFromSongbookKey(0)) // A
        assertEquals(0, Chords.pitchFromSongbookKey(3)) // C
        assertEquals(4, Chords.pitchFromSongbookKey(7)) // E
    }

    @Test
    fun detectsChordLines() {
        assertTrue(Chords.isChordLine("   E      B     C#m    A"))
        assertTrue(Chords.isChordLine("| G / D | Em |"))
        assertFalse(Chords.isChordLine("A man walks down the street"))
    }
}

class ChordProTest {
    @Test
    fun keepsMidWordChordsInsideTheWord() {
        val line = ChordPro.parse("Down on Broa[Amaj7]dway now").single() as SongLine.Lyrics
        assertTrue(line.hasChords)
        assertEquals(4, line.words.size)
        val broadway = line.words[2]
        assertEquals(listOf(Part(null, "Broa"), Part("Amaj7", "dway ")), broadway.parts)
    }

    @Test
    fun chordAtLineStartAndBetweenWords() {
        val line = ChordPro.parse("[E]Grew up [A] in town").single() as SongLine.Lyrics
        assertEquals(Part("E", "Grew "), line.words[0].parts.single())
        assertEquals(Part(null, "up "), line.words[1].parts.single())
        assertEquals(Part("A", " "), line.words[2].parts.single())
    }

    @Test
    fun parsesSectionsTabsAndGrids() {
        val lines = ChordPro.parse(
            """
            {c: Chorus}
            {sot}
            e|--0--|
            {eot}
            {start_of_grid}
            | G / D | Em |
            {end_of_grid}
            Verse 2
            """.trimIndent()
        )
        assertEquals(SongLine.Section("Chorus"), lines[0])
        assertEquals(SongLine.Mono("e|--0--|", transpose = false), lines[1])
        assertEquals(SongLine.Mono("| G / D | Em |", transpose = true), lines[2])
        assertEquals(SongLine.Section("Verse 2"), lines[3])
    }

    @Test
    fun readsStandaloneFiles() {
        val meta = ChordPro.readFile("{title: Song}\n{artist: Band}\n{key: Am}\n{capo: 2}\n[Am]Hello")
        assertEquals("Song", meta.title)
        assertEquals("Band", meta.artist)
        assertEquals(0, meta.keyPitch) // Am -> relative major C
        assertEquals(2, meta.capo)
        assertEquals("[Am]Hello", meta.body)
    }
}

class TranspositionTest {
    private fun song(content: String, key: Int?, shift: Int = 0, capo: Int = 0) =
        Song("x", "t", "a", key, shift, capo, content)

    @Test
    fun appliesShiftAndCapo() {
        val s = song("[E]Hi [A]there", key = 4, shift = 8)
        val t = Transposition.of(s, totalShift = 8, capo = 0)
        assertEquals("C", t.soundingKey)
        assertEquals("C", t.chord("E"))
        assertNull(t.shapeKey)

        val withCapo = Transposition.of(s, totalShift = 8, capo = 3)
        assertEquals("C", withCapo.soundingKey)
        assertEquals("A", withCapo.shapeKey)
        assertEquals("A", withCapo.chord("E"))
    }

    @Test
    fun namesMinorKeys() {
        val s = song("[Am]Hello [G]world", key = 0)
        assertEquals("Am", Transposition.of(s, 0, 0).soundingKey)
        assertEquals("Bm", Transposition.of(s, 2, 0).soundingKey)
    }

    @Test
    fun usesFlatsInFlatKeys() {
        val s = song("[C]Hi [A]x", key = 0)
        val t = Transposition.of(s, 3, 0) // Eb
        assertEquals("Eb", t.soundingKey)
        assertEquals("C", t.chord("A"))
        assertEquals("Eb", t.chord("C"))
    }

    @Test
    fun keepsColumnsInAlignedLines() {
        val t = Transposition.of(song("[A]x", key = 9), 1, 0) // A -> Bb
        assertEquals("Bb      Eb    F", SongRendering.transposeAligned("A       D     E", t))
    }
}

class ImporterTest {
    private val data = """1.0
{"songs":[
 {"Id":3,"name":"Song A","author":"Band","key":7,"KeyShift":8,"Capo":0,"content":"[E]Hi","type":1,"Deleted":false},
 {"Id":4,"name":"Gone","author":"","key":0,"KeyShift":0,"Capo":0,"content":"x","type":1,"Deleted":true},
 {"Id":5,"name":"Song B","author":"Other","key":3,"KeyShift":0,"Capo":1,"content":"[C]Yo","type":1,"Deleted":false}
],"sets":[{"details":{"Id":9,"name":"Gig","date":"2026-07-09T18:54:09","Deleted":0},
 "contents":[{"SongId":5,"Order":1,"keyOfset":2,"Capo":0,"Deleted":0,"ItemType":1},
             {"SongId":3,"Order":0,"keyOfset":0,"Capo":0,"Deleted":0,"ItemType":1},
             {"SongId":4,"Order":2,"keyOfset":0,"Capo":0,"Deleted":0,"ItemType":1}]}],
"folders":[]}"""

    @Test
    fun parsesBackupData() {
        val lib = Importer.parseBackupData(data)
        assertEquals(listOf("Song A", "Song B"), lib.songs.map { it.title })
        assertEquals(4, lib.songs[0].keyPitch)
        assertEquals(8, lib.songs[0].keyShift)
        val set = lib.sets.single()
        assertEquals("Gig", set.name)
        assertEquals("2026-07-09", set.date)
        assertEquals(listOf("sbp:3", "sbp:5"), set.entries.map { it.songId })
        assertEquals(2, set.entries[1].keyOffset)
    }

    @Test
    fun detectsZippedBackup() {
        val zip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("dataFile.txt")); z.write(data.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("settings.hive")); z.write(byteArrayOf(1, 2, 3)); z.closeEntry()
            }
        }.toByteArray()
        val payload = Importer.detect(zip, "Backup.sbpbackup")
        val backup = assertIs<Importer.Payload.Backup>(payload)
        assertEquals(2, backup.library.songs.size)
    }

    @Test
    fun detectsChordProText() {
        val payload = Importer.detect("{title: X}\n[G]la".toByteArray(), "x.cho")
        assertIs<Importer.Payload.ChordProFiles>(payload)
    }

    @Test
    fun searchFindsTitlesThenLyrics() {
        val lib = Library(
            listOf(
                Song("1", "Über den Wolken", "Reinhard", 0, 0, 0, "[C]grenzenlos"),
                Song("2", "Freiheit", "M", 0, 0, 0, "über den Wolken"),
            ),
            emptyList(),
        )
        assertEquals(listOf("1", "2"), lib.search("uber wolken").map { it.id })
    }
}
