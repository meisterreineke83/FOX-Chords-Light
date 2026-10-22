package io.github.lightsongbook.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** A setlist made on the phone. Song ids refer to [Song.id]. */
data class UserSet(val id: String, val name: String, val date: String, val songIds: List<String>)

/**
 * Data the user creates on the phone and that a new backup must not overwrite:
 * own setlists (`mysets.json`) and removed songs (`hidden.txt`).
 */
class UserData(private val root: File) {

    private val setsFile = File(root, "mysets.json")
    private val hiddenFile = File(root, "hidden.txt")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loadSets(): List<UserSet> {
        if (!setsFile.isFile) return emptyList()
        val rootObj = try {
            json.parseToJsonElement(setsFile.readText()) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val sets = rootObj["sets"] as? JsonArray ?: return emptyList()
        return sets.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            UserSet(
                id = id,
                name = (o["name"] as? JsonPrimitive)?.content ?: "Setlist",
                date = (o["date"] as? JsonPrimitive)?.content ?: "",
                songIds = (o["songs"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
            )
        }
    }

    fun saveSets(sets: List<UserSet>) {
        val obj = buildJsonObject {
            put("version", 1)
            put("sets", buildJsonArray {
                for (s in sets) {
                    add(buildJsonObject {
                        put("id", s.id)
                        put("name", s.name)
                        put("date", s.date)
                        put("songs", buildJsonArray { s.songIds.forEach { add(JsonPrimitive(it)) } })
                    })
                }
            })
        }
        writeAtomically(setsFile, obj.toString())
    }

    fun loadHidden(): Set<String> =
        if (hiddenFile.isFile) hiddenFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()

    fun saveHidden(ids: Set<String>) {
        writeAtomically(hiddenFile, ids.sorted().joinToString("\n"))
    }

    private fun writeAtomically(target: File, text: String) {
        root.mkdirs()
        val tmp = File(root, ".${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        fun newSet(name: String): UserSet =
            UserSet("my:" + UUID.randomUUID().toString(), name.trim().ifEmpty { "Neue Setlist" }, LocalDate.now().toString(), emptyList())

        /** Moves the item at [index] by [delta] places; out-of-range moves change nothing. */
        fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
            val target = index + delta
            if (index !in indices || target !in indices) return this
            val list = toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            return list
        }
    }
}
