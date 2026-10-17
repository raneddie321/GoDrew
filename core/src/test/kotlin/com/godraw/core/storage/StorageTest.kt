package com.godraw.core.storage

import com.godraw.core.model.Frame
import com.godraw.core.model.Keyframe
import com.godraw.core.model.LayerBlendMode
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectType
import com.godraw.core.model.TransformProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sampleDoc() = ProjectFactory.create("Hero walk", ProjectType.ANIMATION, 1280, 720, 0xFFFFFFFF.toInt(), fps = 24)
        .let { d ->
            val layer = d.layers.last()
            val anim = layer.animation.withTrack(TransformProperty.POSITION_X, listOf(Keyframe(0, 0f), Keyframe(10, 50f)))
            d.copy(
                layers = d.layers.dropLast(1) + layer.copy(animation = anim, blendMode = LayerBlendMode.MULTIPLY, opacity = 0.5f),
                frames = d.frames + Frame("second", hold = 2),
            )
        }

    @Test
    fun projectJsonRoundTrip() {
        val doc = sampleDoc()
        val decoded = ProjectJson.decode(ProjectJson.encode(doc))
        assertEquals(doc, decoded)
    }

    @Test
    fun decodeRepairsInvalidActiveLayer() {
        val doc = sampleDoc().copy(activeLayerId = "missing", activeFrameIndex = 99)
        val decoded = ProjectJson.decode(ProjectJson.encode(doc))
        assertEquals(doc.layers.last().id, decoded.activeLayerId)
        assertEquals(1, decoded.activeFrameIndex)
    }

    @Test(expected = UnsupportedProjectVersionException::class)
    fun rejectsFutureFormatVersions() {
        ProjectJson.decode(ProjectJson.encode(sampleDoc().copy(formatVersion = 999)))
    }

    @Test
    fun atomicWriteKeepsBackupAndRecovers() {
        val f = File(tmp.root, "project.json")
        AtomicFiles.writeText(f, "first", keepBackup = true)
        AtomicFiles.writeText(f, "second", keepBackup = true)
        assertEquals("second", f.readText())
        assertEquals("first", File(tmp.root, "project.json.bak").readText())

        // Corrupt primary: recovery falls back to the backup.
        f.writeText("garbage")
        val value = AtomicFiles.readWithRecovery(f) { if (it == "garbage") error("corrupt") else it }
        assertEquals("first", value)
    }

    @Test
    fun interruptedWriteLeavesOriginalIntact() {
        val f = File(tmp.root, "cell.png")
        AtomicFiles.writeText(f, "good")
        try {
            AtomicFiles.write(f) { out -> out.write(1); throw IOException("disk full") }
            fail("expected exception")
        } catch (_: IOException) {
        }
        assertEquals("good", f.readText())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun cleanupRemovesLeftoverTempFiles() {
        File(tmp.root, "a.png.tmp").writeText("x")
        File(tmp.root, "sub").mkdirs()
        File(tmp.root, "sub/b.json.tmp").writeText("x")
        assertEquals(2, AtomicFiles.cleanupInterrupted(tmp.root))
    }

    @Test
    fun archivePackUnpackRoundTrip() {
        val src = tmp.newFolder("proj")
        File(src, "project.json").writeText(ProjectJson.encode(sampleDoc()))
        File(src, "cells").mkdirs()
        File(src, "cells/cell_a_b.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(src, "audio").mkdirs()
        File(src, "audio/song.m4a").writeBytes(ByteArray(1000) { it.toByte() })
        File(src, "ignored.tmp").writeText("partial")

        val bytes = ByteArrayOutputStream().also { ProjectArchive.pack(src, it) }.toByteArray()
        val dest = tmp.newFolder("restored")
        val files = ProjectArchive.unpack(bytes.inputStream(), dest)
        assertEquals(3, files.size)
        assertNotNull(ProjectJson.decode(File(dest, "project.json").readText()))
        assertTrue(File(dest, "cells/cell_a_b.png").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals(1000L, File(dest, "audio/song.m4a").length())
    }

    @Test(expected = IOException::class)
    fun unpackRejectsZipSlip() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("../evil.txt"))
                zip.write("x".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        ProjectArchive.unpack(bytes.inputStream(), tmp.newFolder("dest"))
    }
}
