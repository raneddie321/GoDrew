package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.godraw.core.history.UndoableCommand
import com.godraw.core.model.CellKey
import com.godraw.core.model.ProjectDocument

/** Region-based pixel undo: stores only the changed rectangle before and after. */
class PixelChangeCommand(
    private val engine: DrawingEngine,
    override val label: String,
    private val key: CellKey,
    private val rect: Rect,
    private val before: Bitmap,
    private val after: Bitmap,
) : UndoableCommand {
    override val sizeBytes: Long get() = before.allocationByteCount.toLong() + after.allocationByteCount
    override fun undo() = engine.writeRegion(key, rect, before)
    override fun redo() = engine.writeRegion(key, rect, after)

    companion object {
        private val copyPaint = Paint().apply { blendMode = BlendMode.SRC }

        fun crop(source: Bitmap?, rect: Rect): Bitmap {
            val out = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            if (source != null) Canvas(out).drawBitmap(source, -rect.left.toFloat(), -rect.top.toFloat(), copyPaint)
            return out
        }

        fun paste(target: Bitmap, rect: Rect, pixels: Bitmap) {
            Canvas(target).drawBitmap(pixels, rect.left.toFloat(), rect.top.toFloat(), copyPaint)
        }
    }
}

/**
 * Structural undo: document state plus the cells that changed. Snapshots are shared rather than
 * copied on restore; this is safe because history is linear (see [CellSnapshot]).
 */
class DocumentChangeCommand(
    private val engine: DrawingEngine,
    override val label: String,
    private val beforeDoc: ProjectDocument,
    private val afterDoc: ProjectDocument,
    private val beforeCells: Map<CellKey, CellSnapshot> = emptyMap(),
    private val afterCells: Map<CellKey, CellSnapshot> = emptyMap(),
) : UndoableCommand {
    override val sizeBytes: Long
        get() = beforeCells.values.sumOf { it.bytes } + afterCells.values.sumOf { it.bytes } + 4096

    override fun undo() = engine.restoreState(beforeDoc, beforeCells)
    override fun redo() = engine.restoreState(afterDoc, afterCells)

    override fun dispose() {
        (beforeCells.values + afterCells.values).forEach { if (it is CellSnapshot.OnDisk) it.file.delete() }
    }
}
