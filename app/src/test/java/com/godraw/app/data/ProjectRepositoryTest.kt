package com.godraw.app.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.app.data.db.AppDatabase
import com.godraw.app.data.project.ProjectRepository
import com.godraw.core.model.CellKey
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectType
import com.godraw.core.storage.ProjectArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.inMemory(context)
        repo = ProjectRepository(context, db, Dispatchers.Unconfined)
        repo.projectsDir.deleteRecursively()
        repo.projectsDir.mkdirs()
    }

    @After
    fun tearDown() {
        db.close()
        repo.projectsDir.deleteRecursively()
    }

    private fun newDoc(name: String = "Test", type: ProjectType = ProjectType.DRAWING) =
        ProjectFactory.create(name, type, 64, 48, Color.WHITE)

    private fun redDot(): Bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { setPixel(10, 10, Color.RED) }

    @Test
    fun createSaveAndReopen() = runBlocking {
        val doc = newDoc()
        repo.create(doc)
        val key = CellKey.forLayer(doc.layers.last(), doc.frames.first().id)
        repo.save(doc.copy(name = "Renamed"), mapOf(key to redDot()), thumbnail = redDot())

        val loaded = repo.load(doc.id)
        assertEquals("Renamed", loaded.name)
        assertEquals(doc.layers, loaded.layers)
        assertEquals(Color.RED, repo.loadCell(doc.id, key)!!.getPixel(10, 10))
        assertEquals(1, repo.projectCount())
        assertTrue(repo.thumbnailFile(doc.id).exists())
        assertEquals("Renamed", repo.entity(doc.id)!!.name)
    }

    @Test
    fun deletingACellAndOrphansIsPersisted() = runBlocking {
        val doc = newDoc()
        repo.create(doc)
        val key = CellKey.forLayer(doc.layers.last(), doc.frames.first().id)
        repo.save(doc, mapOf(key to redDot()), null)
        // A cell for a layer that no longer exists is removed on the next save.
        val orphan = File(repo.cellsDir(doc.id), CellKey(doc.frames.first().id, "gone").fileName)
        orphan.writeText("x")
        orphan.setLastModified(System.currentTimeMillis() - 60_000)
        // A file written while a save is running (e.g. a spilled cell of a new frame) is kept.
        val fresh = File(repo.cellsDir(doc.id), CellKey("new_frame", doc.layers.last().id).fileName)
        fresh.writeText("y")
        repo.save(doc, mapOf(key to null), null)
        assertTrue(fresh.exists())
        assertEquals(null, repo.loadCell(doc.id, key))
        assertFalse(orphan.exists())
    }

    @Test
    fun renameDuplicateMoveDelete() = runBlocking {
        val doc = newDoc()
        repo.create(doc)
        repo.save(doc, mapOf(CellKey.forLayer(doc.layers.last(), doc.frames.first().id) to redDot()), null)
        repo.rename(doc.id, "  Sunset  ")
        assertEquals("Sunset", repo.load(doc.id).name)

        val copyId = repo.duplicate(doc.id, "Sunset copy")
        assertNotEquals(doc.id, copyId)
        val copy = repo.load(copyId)
        assertEquals("Sunset copy", copy.name)
        assertEquals(Color.RED, repo.loadCell(copyId, CellKey.forLayer(copy.layers.last(), copy.frames.first().id))!!.getPixel(10, 10))

        val folder = repo.createFolder("Comics")
        repo.move(listOf(copyId), folder)
        assertEquals(folder, repo.entity(copyId)!!.folderId)
        repo.deleteFolder(folder)
        assertEquals(null, repo.entity(copyId)!!.folderId)
        assertEquals(2, repo.projectCount())

        repo.delete(doc.id)
        assertEquals(1, repo.projectCount())
        assertFalse(repo.projectDir(doc.id).exists())
    }

    @Test
    fun exportAndImportGodrawFile() = runBlocking {
        val doc = newDoc("Portable", ProjectType.ANIMATION)
        repo.create(doc)
        val key = CellKey.forLayer(doc.layers.last(), doc.frames.first().id)
        repo.save(doc, mapOf(key to redDot()), redDot())
        val bytes = ByteArrayOutputStream().also { repo.exportProject(doc.id, it) }.toByteArray()

        val importedId = repo.importProject(bytes.inputStream())
        assertNotEquals(doc.id, importedId) // id collision → new id, original untouched
        val imported = repo.load(importedId)
        assertEquals("Portable", imported.name)
        assertEquals(ProjectType.ANIMATION, imported.type)
        assertEquals(Color.RED, repo.loadCell(importedId, key)!!.getPixel(10, 10))
    }

    @Test
    fun backupAndRestoreAllProjects() = runBlocking {
        val a = newDoc("A")
        val b = newDoc("B")
        repo.create(a)
        repo.create(b)
        val backup = ByteArrayOutputStream().also { assertEquals(2, repo.backupAll(it)) }.toByteArray()
        repo.delete(a.id)
        repo.delete(b.id)
        assertEquals(0, repo.projectCount())

        assertEquals(2, repo.restoreBackup(backup.inputStream()))
        assertEquals(setOf("A", "B"), listOf(a.id, b.id).map { repo.load(it).name }.toSet())
    }

    @Test
    fun corruptedManifestRecoversFromBackup() = runBlocking {
        val doc = newDoc("First")
        repo.create(doc)
        repo.save(doc.copy(name = "Second"), emptyMap(), null)
        File(repo.projectDir(doc.id), ProjectArchive.MANIFEST).writeText("{ broken")
        assertEquals("First", repo.load(doc.id).name)
    }

    @Test
    fun recoveryReindexesAndCleansInterruptedSaves() = runBlocking {
        val doc = newDoc("Crash")
        repo.create(doc)
        // Simulate a crash: database row lost, temp file left behind.
        db.projectDao().delete(doc.id)
        File(repo.cellsDir(doc.id), "cell_x_y.png.tmp").writeText("partial")
        val report = repo.recover()
        assertEquals(1, report.reindexedProjects)
        assertEquals(1, report.removedTempFiles)
        assertNotNull(repo.entity(doc.id))
    }
}
