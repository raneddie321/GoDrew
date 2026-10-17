package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.godraw.core.model.CellKey
import com.godraw.core.model.Ids
import java.io.File

/** A saved state of one cell for undo/redo. Large multi-frame operations spill to disk. */
sealed class CellSnapshot {
    abstract val bytes: Long

    data object Empty : CellSnapshot() {
        override val bytes: Long = 0
    }

    class InMemory(val bitmap: Bitmap) : CellSnapshot() {
        override val bytes: Long get() = bitmap.allocationByteCount.toLong()
    }

    class OnDisk(val file: File) : CellSnapshot() {
        override val bytes: Long = 0
    }
}

/**
 * Captures and restores [CellSnapshot]s. In-memory snapshots are used until [memoryBudget] bytes
 * have been captured by the current operation; further snapshots are PNG-encoded into [undoDir].
 */
class SnapshotManager(
    private val store: CellStore,
    private val undoDir: File,
    val cellFile: (CellKey) -> File,
) {
    var memoryBudget: Long = 96L * 1024 * 1024
    private var usedInOperation = 0L

    fun beginOperation() {
        usedInOperation = 0L
    }

    fun capture(key: CellKey): CellSnapshot {
        if (store.isLoaded(key)) {
            val bmp = store.get(key) ?: return CellSnapshot.Empty
            val size = bmp.allocationByteCount.toLong()
            return if (usedInOperation + size <= memoryBudget) {
                usedInOperation += size
                CellSnapshot.InMemory(bmp.copy(Bitmap.Config.ARGB_8888, true))
            } else {
                encode(bmp)
            }
        }
        if (store.isKnownEmpty(key)) return CellSnapshot.Empty
        val source = cellFile(key)
        if (!source.exists()) return CellSnapshot.Empty
        undoDir.mkdirs()
        val target = File(undoDir, Ids.newId() + ".png")
        source.copyTo(target, overwrite = true)
        return CellSnapshot.OnDisk(target)
    }

    private fun encode(bmp: Bitmap): CellSnapshot {
        undoDir.mkdirs()
        val target = File(undoDir, Ids.newId() + ".png")
        target.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return CellSnapshot.OnDisk(target)
    }

    fun restore(key: CellKey, snapshot: CellSnapshot) {
        when (snapshot) {
            CellSnapshot.Empty -> store.set(key, null)
            is CellSnapshot.InMemory -> store.set(key, snapshot.bitmap)
            is CellSnapshot.OnDisk -> {
                val bmp = BitmapFactory.decodeFile(
                    snapshot.file.path,
                    BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 },
                )
                store.set(key, bmp)
            }
        }
    }

    fun clear() {
        undoDir.deleteRecursively()
    }
}
