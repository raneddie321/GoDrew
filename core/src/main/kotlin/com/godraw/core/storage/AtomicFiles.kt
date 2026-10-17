package com.godraw.core.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Crash-safe file writes: data goes to a temporary sibling, is fsync'ed, then atomically renamed
 * over the target. The previous version is kept as `<name>.bak` so a corrupted file can be
 * recovered. An interrupted write therefore never destroys the last good copy.
 */
object AtomicFiles {

    const val TMP_SUFFIX = ".tmp"
    const val BAK_SUFFIX = ".bak"

    fun write(target: File, keepBackup: Boolean = false, block: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        try {
            FileOutputStream(tmp).use { fos ->
                block(fos)
                fos.flush()
                fos.fd.sync()
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        if (keepBackup && target.exists()) {
            val bak = File(target.parentFile, target.name + BAK_SUFFIX)
            bak.delete()
            if (!target.renameTo(bak)) {
                target.copyTo(bak, overwrite = true)
            }
        }
        if (!tmp.renameTo(target)) {
            // Some file systems refuse rename-over; fall back to delete + rename.
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Could not move ${tmp.name} into place")
            }
        }
    }

    fun writeText(target: File, text: String, keepBackup: Boolean = false) =
        write(target, keepBackup) { it.write(text.toByteArray(Charsets.UTF_8)) }

    /**
     * Reads [target], falling back to its backup when the primary is missing or [validate] fails.
     * Returns null when neither copy is usable.
     */
    fun <T> readWithRecovery(target: File, parse: (String) -> T): T? {
        val candidates = listOf(target, File(target.parentFile, target.name + BAK_SUFFIX))
        for (file in candidates) {
            if (!file.exists()) continue
            try {
                return parse(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // try the next candidate
            }
        }
        return null
    }

    /** Removes temp files left behind by writes interrupted by a crash or power loss. */
    fun cleanupInterrupted(dir: File): Int {
        val leftovers = dir.walkTopDown().filter { it.isFile && it.name.endsWith(TMP_SUFFIX) }.toList()
        leftovers.forEach { it.delete() }
        return leftovers.size
    }
}
