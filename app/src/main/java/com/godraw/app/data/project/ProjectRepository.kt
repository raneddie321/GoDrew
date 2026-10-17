package com.godraw.app.data.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.godraw.app.data.db.AppDatabase
import com.godraw.app.data.db.FolderEntity
import com.godraw.app.data.db.ProjectEntity
import com.godraw.core.model.AudioClip
import com.godraw.core.model.CellKey
import com.godraw.core.model.Ids
import com.godraw.core.model.ProjectDocument
import com.godraw.core.storage.AtomicFiles
import com.godraw.core.storage.ProjectArchive
import com.godraw.core.storage.ProjectJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class ProjectLoadException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class RecoveryReport(val removedTempFiles: Int, val reindexedProjects: Int, val removedEntries: Int)

/**
 * Owns project storage. Each project is a directory under `files/projects/<id>/`:
 *
 *  project.json      document (written atomically, previous version kept as .bak)
 *  cells/            one PNG per layer per frame (missing file = empty cell)
 *  audio/            imported audio, copied in so projects stay self-contained
 *  thumbnail.png     gallery preview
 *
 * Room mirrors the metadata for fast listing, searching and sorting.
 */
class ProjectRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dao = db.projectDao()
    private val locks = ConcurrentHashMap<String, Mutex>()

    val projectsDir: File = File(context.filesDir, "projects").apply { mkdirs() }

    val projects: Flow<List<ProjectEntity>> = dao.observeProjects()
    val folders: Flow<List<FolderEntity>> = dao.observeFolders()

    fun projectDir(id: String) = File(projectsDir, id)
    fun cellsDir(id: String) = File(projectDir(id), CELLS)
    fun audioDir(id: String) = File(projectDir(id), AUDIO)
    fun audioFile(id: String, clip: AudioClip) = File(audioDir(id), clip.fileName)
    fun thumbnailFile(id: String) = File(projectDir(id), THUMBNAIL)
    private fun manifest(id: String) = File(projectDir(id), ProjectArchive.MANIFEST)

    private fun lock(id: String) = locks.getOrPut(id) { Mutex() }

    suspend fun projectCount(): Int = withContext(io) { dao.projectCount() }

    suspend fun entity(id: String): ProjectEntity? = withContext(io) { dao.project(id) }

    /** Creates a new project on disk with optional initial cell contents. */
    suspend fun create(doc: ProjectDocument, initialCells: Map<CellKey, Bitmap> = emptyMap(), folderId: String? = null): String =
        withContext(io) {
            lock(doc.id).withLock {
                val dir = projectDir(doc.id)
                dir.mkdirs()
                cellsDir(doc.id).mkdirs()
                initialCells.forEach { (key, bmp) -> writeCell(doc.id, key, bmp) }
                AtomicFiles.writeText(manifest(doc.id), ProjectJson.encode(doc), keepBackup = true)
                dao.upsert(entityFor(doc, folderId = folderId, thumbnailVersion = 0))
            }
            doc.id
        }

    suspend fun load(id: String): ProjectDocument = withContext(io) {
        lock(id).withLock {
            AtomicFiles.readWithRecovery(manifest(id)) { ProjectJson.decode(it) }
                ?: throw ProjectLoadException("Project $id could not be read")
        }
    }

    /** Decodes one cell; returns null for cells that were never drawn. Call off the main thread. */
    fun loadCell(projectId: String, key: CellKey): Bitmap? {
        val file = File(cellsDir(projectId), key.fileName)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /**
     * Saves the document and the given cells. [changedCells] maps cell keys to snapshots (the
     * caller passes copies so drawing can continue during the save); null deletes the cell file.
     */
    suspend fun save(
        doc: ProjectDocument,
        changedCells: Map<CellKey, Bitmap?>,
        thumbnail: Bitmap?,
    ) = withContext(io) {
        lock(doc.id).withLock {
            cellsDir(doc.id).mkdirs()
            for ((key, bitmap) in changedCells) {
                if (bitmap == null) File(cellsDir(doc.id), key.fileName).delete() else writeCell(doc.id, key, bitmap)
            }
            removeOrphanCells(doc)
            AtomicFiles.writeText(manifest(doc.id), ProjectJson.encode(doc), keepBackup = true)
            var thumbVersion = dao.project(doc.id)?.thumbnailVersion ?: 0
            if (thumbnail != null) {
                AtomicFiles.write(thumbnailFile(doc.id)) { thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it) }
                thumbVersion++
            }
            val folder = dao.project(doc.id)?.folderId
            dao.upsert(entityFor(doc, folderId = folder, thumbnailVersion = thumbVersion))
        }
    }

    private fun writeCell(projectId: String, key: CellKey, bitmap: Bitmap) {
        AtomicFiles.write(File(cellsDir(projectId), key.fileName)) { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
        }
    }

    /** Deletes cell files of layers or frames that no longer exist in the document. */
    private fun removeOrphanCells(doc: ProjectDocument) {
        val valid = HashSet<String>()
        for (layer in doc.layers) {
            if (layer.isGroup) continue
            val frameIds = if (layer.isStatic) listOf(ProjectDocument.STATIC_FRAME_ID) else doc.frames.map { it.id }
            for (f in frameIds) {
                valid += CellKey(f, layer.id).fileName
                valid += CellKey(f, layer.id, isMask = true).fileName
            }
        }
        cellsDir(doc.id).listFiles()?.forEach { f ->
            if (f.name.endsWith(".png") && f.name !in valid) f.delete()
        }
    }

    private fun entityFor(doc: ProjectDocument, folderId: String?, thumbnailVersion: Long) = ProjectEntity(
        id = doc.id,
        name = doc.name,
        type = doc.type.name,
        width = doc.width,
        height = doc.height,
        fps = doc.fps,
        frameCount = doc.frames.size,
        layerCount = doc.rasterLayerCount,
        folderId = folderId,
        createdAt = doc.createdAt,
        updatedAt = doc.modifiedAt,
        thumbnailVersion = thumbnailVersion,
        sizeBytes = directorySize(projectDir(doc.id)),
    )

    private fun directorySize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    suspend fun rename(id: String, name: String) = withContext(io) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return@withContext
        lock(id).withLock {
            val doc = AtomicFiles.readWithRecovery(manifest(id)) { ProjectJson.decode(it) } ?: return@withLock
            val now = System.currentTimeMillis()
            AtomicFiles.writeText(manifest(id), ProjectJson.encode(doc.copy(name = clean, modifiedAt = now)), keepBackup = true)
            dao.rename(id, clean, now)
        }
    }

    suspend fun duplicate(id: String, copyName: String): String = withContext(io) {
        val newId = Ids.newId()
        lock(id).withLock {
            val src = projectDir(id)
            val dst = projectDir(newId)
            src.copyRecursively(dst, overwrite = true)
            AtomicFiles.cleanupInterrupted(dst)
            File(dst, ProjectArchive.MANIFEST + AtomicFiles.BAK_SUFFIX).delete()
            val doc = AtomicFiles.readWithRecovery(File(dst, ProjectArchive.MANIFEST)) { ProjectJson.decode(it) }
                ?: throw ProjectLoadException("Cannot duplicate $id")
            val now = System.currentTimeMillis()
            val copy = doc.copy(id = newId, name = copyName.take(MAX_NAME), createdAt = now, modifiedAt = now)
            AtomicFiles.writeText(File(dst, ProjectArchive.MANIFEST), ProjectJson.encode(copy), keepBackup = true)
            val folder = dao.project(id)?.folderId
            dao.upsert(entityFor(copy, folderId = folder, thumbnailVersion = 1))
        }
        newId
    }

    suspend fun delete(id: String) = withContext(io) {
        lock(id).withLock {
            dao.delete(id)
            projectDir(id).deleteRecursively()
        }
        locks.remove(id)
    }

    suspend fun move(ids: List<String>, folderId: String?) = withContext(io) { dao.move(ids, folderId) }

    suspend fun createFolder(name: String): String = withContext(io) {
        val id = Ids.newId()
        dao.upsertFolder(FolderEntity(id, name.trim().take(MAX_NAME), System.currentTimeMillis()))
        id
    }

    suspend fun renameFolder(folder: FolderEntity, name: String) = withContext(io) {
        dao.upsertFolder(folder.copy(name = name.trim().take(MAX_NAME)))
    }

    /** Deletes a folder; its projects are kept and moved back to the top level. */
    suspend fun deleteFolder(id: String) = withContext(io) {
        dao.clearFolder(id)
        dao.deleteFolder(id)
    }

    // ------------------------------------------------------------------ import / export / backup

    suspend fun exportProject(id: String, out: OutputStream) = withContext(io) {
        lock(id).withLock { ProjectArchive.pack(projectDir(id), out) }
    }

    /** Imports a `.godraw` archive as a new project and returns its id. */
    suspend fun importProject(input: InputStream): String = withContext(io) {
        val staging = File(context.cacheDir, "import_" + Ids.newId())
        try {
            ProjectArchive.unpack(input, staging)
            adoptProjectDir(staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Writes every project into one backup archive. */
    suspend fun backupAll(out: OutputStream): Int = withContext(io) {
        val dirs = dao.allProjects().map { projectDir(it.id) }.filter { File(it, ProjectArchive.MANIFEST).exists() }
        ProjectArchive.packMany(dirs, out)
        dirs.size
    }

    /** Restores projects from a backup archive. Existing projects are never overwritten. */
    suspend fun restoreBackup(input: InputStream): Int = withContext(io) {
        val staging = File(context.cacheDir, "restore_" + Ids.newId())
        try {
            ProjectArchive.unpack(input, staging)
            val roots = if (File(staging, ProjectArchive.MANIFEST).exists()) {
                listOf(staging)
            } else {
                staging.listFiles()?.filter { File(it, ProjectArchive.MANIFEST).exists() }.orEmpty()
            }
            roots.count { runCatching { adoptProjectDir(it) }.isSuccess }
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun adoptProjectDir(dir: File): String {
        val manifestFile = File(dir, ProjectArchive.MANIFEST)
        val doc = AtomicFiles.readWithRecovery(manifestFile) { ProjectJson.decode(it) }
            ?: throw ProjectLoadException("Archive does not contain a GoDraw project")
        val newId = if (projectDir(doc.id).exists()) Ids.newId() else doc.id
        val target = projectDir(newId)
        dir.copyRecursively(target, overwrite = true)
        AtomicFiles.cleanupInterrupted(target)
        val adopted = doc.copy(id = newId)
        AtomicFiles.writeText(File(target, ProjectArchive.MANIFEST), ProjectJson.encode(adopted), keepBackup = false)
        dao.upsert(entityFor(adopted, folderId = null, thumbnailVersion = 1))
        return newId
    }

    /**
     * Startup recovery: removes temp files from interrupted saves, re-indexes projects whose
     * metadata is missing (e.g. after a crash between writing files and updating the database)
     * and drops index entries whose files are gone.
     */
    suspend fun recover(): RecoveryReport = withContext(io) {
        val removedTemp = AtomicFiles.cleanupInterrupted(projectsDir)
        val known = dao.allProjects().associateBy { it.id }
        var reindexed = 0
        projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (dir.name !in known) {
                val doc = AtomicFiles.readWithRecovery(File(dir, ProjectArchive.MANIFEST)) { ProjectJson.decode(it) }
                if (doc != null && doc.id == dir.name) {
                    dao.upsert(entityFor(doc, folderId = null, thumbnailVersion = 1))
                    reindexed++
                }
            }
        }
        var removed = 0
        known.values.forEach { entity ->
            if (!projectDir(entity.id).exists()) {
                dao.delete(entity.id)
                removed++
            }
        }
        RecoveryReport(removedTemp, reindexed, removed)
    }

    // ------------------------------------------------------------------ audio assets

    /** Copies an audio document into the project so it stays available offline. */
    suspend fun importAudio(projectId: String, uri: Uri): AudioClip = withContext(io) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "audio"
        val ext = displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 } ?: "audio"
        val clipId = Ids.newId()
        val fileName = "$clipId.$ext"
        val target = File(audioDir(projectId), fileName)
        audioDir(projectId).mkdirs()
        AtomicFiles.write(target) { out ->
            resolver.openInputStream(uri)?.use { it.copyTo(out) } ?: throw IOException("Cannot open audio")
        }
        val duration = MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(target.path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                0L
            } finally {
                r.release()
            }
        }
        if (duration <= 0L) {
            target.delete()
            throw IOException("Unsupported audio file")
        }
        AudioClip(id = clipId, fileName = fileName, displayName = displayName.substringBeforeLast('.'), durationMs = duration)
    }

    companion object {
        const val CELLS = "cells"
        const val AUDIO = "audio"
        const val THUMBNAIL = "thumbnail.png"
        const val MAX_NAME = 80
    }
}
