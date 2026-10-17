package com.godraw.app.engine

import android.graphics.Bitmap
import com.godraw.core.model.CellKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the bitmaps of the open project's cells (one per layer per frame). Cells load lazily,
 * least-recently-used cells are evicted when [budgetBytes] is exceeded, and evicted cells with
 * unsaved changes are first written to disk through [spill] so no work is lost.
 *
 * Must be used from the main thread, except for the loader which may run anywhere.
 */
class CellStore(
    val width: Int,
    val height: Int,
    private val loader: (CellKey) -> Bitmap?,
    private val spill: (CellKey, Bitmap) -> Boolean,
    private val budgetBytes: Long,
) {
    private val loaded = LinkedHashMap<CellKey, Bitmap>(64, 0.75f, true)
    private val knownEmpty = HashSet<CellKey>()
    private val dirty = HashSet<CellKey>()
    private val removed = HashSet<CellKey>()

    /** Cells that must stay in memory (current frame, onion skins, playback window). */
    var pinned: Set<CellKey> = emptySet()

    val cellBytes: Long get() = width.toLong() * height * 4
    val loadedBytes: Long get() = loaded.size * cellBytes
    val hasUnsavedChanges: Boolean get() = dirty.isNotEmpty()

    fun get(key: CellKey): Bitmap? {
        loaded[key]?.let { return it }
        if (key in knownEmpty || key in removed) return null
        val bmp = runCatching { loader(key) }.getOrNull()
        if (bmp == null) {
            knownEmpty += key
        } else {
            loaded[key] = bmp
            trim()
        }
        return bmp
    }

    /** Returns the cell bitmap, creating a transparent one if the cell is empty. */
    fun getOrCreate(key: CellKey): Bitmap {
        get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        loaded[key] = bmp
        knownEmpty -= key
        removed -= key
        trim()
        return bmp
    }

    /** Replaces a cell (null clears it) and marks it as changed. */
    fun set(key: CellKey, bitmap: Bitmap?) {
        if (bitmap == null) {
            loaded.remove(key)
            knownEmpty += key
            removed += key
        } else {
            loaded[key] = bitmap
            knownEmpty -= key
            removed -= key
        }
        dirty += key
        trim()
    }

    fun markDirty(key: CellKey) {
        dirty += key
    }

    fun isLoaded(key: CellKey): Boolean = key in loaded

    fun isKnownEmpty(key: CellKey): Boolean = key in knownEmpty || key in removed

    /**
     * Copies every changed cell for saving. Values are independent copies (null = delete file),
     * so drawing can continue while the save runs in the background.
     */
    fun snapshotChanges(): Map<CellKey, Bitmap?> {
        val result = HashMap<CellKey, Bitmap?>()
        for (key in dirty) {
            when {
                key in removed -> result[key] = null
                loaded[key] != null -> result[key] = loaded[key]!!.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
        return result
    }

    /** Marks the given cells as saved (unless they changed again since the snapshot). */
    fun markSaved(keys: Collection<CellKey>, generationAtSnapshot: Map<CellKey, Int>) {
        for (key in keys) {
            val gen = generationAtSnapshot[key]
            val current = loaded[key]?.generationId
            if (gen == null || current == null || gen == current) {
                dirty -= key
                if (loaded[key] == null) removed -= key
            }
        }
    }

    fun generations(keys: Collection<CellKey>): Map<CellKey, Int> =
        keys.mapNotNull { k -> loaded[k]?.let { k to it.generationId } }.toMap()

    /** Loads cells in the background so playback and frame switching stay smooth. */
    suspend fun prefetch(keys: List<CellKey>) {
        val missing = keys.filter { it !in loaded && it !in knownEmpty && it !in removed }
        if (missing.isEmpty()) return
        val decoded = withContext(Dispatchers.IO) { missing.map { it to runCatching { loader(it) }.getOrNull() } }
        for ((key, bmp) in decoded) {
            if (key in loaded || key in removed || key in dirty) continue
            if (bmp == null) knownEmpty += key else loaded[key] = bmp
        }
        trim()
    }

    /** Forgets cached emptiness, e.g. after cells were written to disk by another component. */
    fun invalidateEmptyCache() {
        knownEmpty.clear()
    }

    private fun trim() {
        if (loadedBytes <= budgetBytes) return
        val iterator = loaded.entries.iterator()
        while (iterator.hasNext() && loadedBytes > budgetBytes) {
            val (key, bmp) = iterator.next()
            if (key in pinned) continue
            if (key in dirty) {
                if (!spill(key, bmp)) continue
                dirty -= key
            }
            iterator.remove()
        }
    }

    fun clear() {
        loaded.clear()
        knownEmpty.clear()
        dirty.clear()
        removed.clear()
    }
}
