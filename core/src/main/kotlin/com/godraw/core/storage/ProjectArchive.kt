package com.godraw.core.storage

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The portable `.godraw` file: a zip of a project directory (project.json, layer cells as PNG,
 * imported audio, thumbnail). Also used for full backups containing several projects.
 */
object ProjectArchive {

    const val EXTENSION = "godraw"
    const val BACKUP_EXTENSION = "godrawbackup"
    const val MANIFEST = "project.json"

    /** Upper bound for a single extracted entry, protecting against zip bombs. */
    private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024

    fun pack(sourceDir: File, out: OutputStream, prefix: String = "") {
        val zip = ZipOutputStream(out)
        writeDir(zip, sourceDir, prefix)
        zip.finish()
        zip.flush()
    }

    /** Packs several project directories under their own folder names into one backup archive. */
    fun packMany(projectDirs: List<File>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        projectDirs.forEach { dir -> writeDir(zip, dir, dir.name + "/") }
        zip.finish()
        zip.flush()
    }

    private fun writeDir(zip: ZipOutputStream, dir: File, prefix: String) {
        dir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(AtomicFiles.TMP_SUFFIX) }
            .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
            .forEach { file ->
                val name = prefix + file.relativeTo(dir).invariantSeparatorsPath
                val entry = ZipEntry(name)
                // PNG and compressed audio are already compressed; store them to save time.
                if (file.extension.lowercase() in setOf("png", "jpg", "mp3", "m4a", "aac", "ogg")) {
                    entry.method = ZipEntry.STORED
                    entry.size = file.length()
                    entry.compressedSize = file.length()
                    entry.crc = crc32(file)
                }
                zip.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }

    private fun crc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    /**
     * Extracts [input] into [destDir], rejecting entries that would escape the directory
     * ("zip slip") or that are unreasonably large. Returns the extracted files.
     */
    fun unpack(input: InputStream, destDir: File): List<File> {
        destDir.mkdirs()
        val root = destDir.canonicalFile
        val files = ArrayList<File>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(root, entry.name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw IOException("Unsafe archive entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    var written = 0L
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            written += n
                            if (written > MAX_ENTRY_BYTES) throw IOException("Archive entry too large: ${entry.name}")
                            out.write(buf, 0, n)
                        }
                    }
                    files += target
                }
                zip.closeEntry()
            }
        }
        return files
    }
}
