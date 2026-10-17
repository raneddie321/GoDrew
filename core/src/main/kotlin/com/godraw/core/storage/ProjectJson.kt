package com.godraw.core.storage

import com.godraw.core.model.ProjectDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Serializes [ProjectDocument] to the native GoDraw JSON format, with version migration. */
object ProjectJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        allowSpecialFloatingPointValues = false
    }

    fun encode(doc: ProjectDocument): String = json.encodeToString(ProjectDocument.serializer(), doc)

    fun decode(text: String): ProjectDocument {
        val element = json.parseToJsonElement(text).jsonObject
        val version = element["formatVersion"]?.jsonPrimitive?.int ?: 1
        if (version > ProjectDocument.CURRENT_FORMAT_VERSION) {
            throw UnsupportedProjectVersionException(version)
        }
        val migrated = migrate(element, version)
        val doc = json.decodeFromJsonElement(ProjectDocument.serializer(), migrated)
        return validate(doc)
    }

    /** Hook for future format upgrades (version N → N+1 transformations on raw JSON). */
    private fun migrate(obj: JsonObject, fromVersion: Int): JsonObject {
        @Suppress("UNUSED_VARIABLE") val v = fromVersion
        return obj
    }

    /** Repairs inconsistent but recoverable state rather than refusing to open the project. */
    private fun validate(doc: ProjectDocument): ProjectDocument {
        require(doc.width > 0 && doc.height > 0) { "Invalid canvas size" }
        require(doc.layers.isNotEmpty()) { "Project has no layers" }
        require(doc.frames.isNotEmpty()) { "Project has no frames" }
        val groupIds = doc.layers.filter { it.isGroup }.map { it.id }.toSet()
        val layers = doc.layers.map { if (it.parentId != null && it.parentId !in groupIds) it.copy(parentId = null) else it }
        val active = if (layers.any { it.id == doc.activeLayerId && !it.isGroup }) {
            doc.activeLayerId
        } else {
            layers.last { !it.isGroup }.id
        }
        return doc.copy(
            layers = layers,
            activeLayerId = active,
            activeFrameIndex = doc.activeFrameIndex.coerceIn(0, doc.frames.lastIndex),
            fps = doc.fps.coerceIn(1, 60),
        )
    }
}

class UnsupportedProjectVersionException(val version: Int) :
    IllegalStateException("Project format version $version is newer than this app supports")
