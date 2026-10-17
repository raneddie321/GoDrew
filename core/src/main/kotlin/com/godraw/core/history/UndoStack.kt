package com.godraw.core.history

/** A reversible edit. Implementations hold whatever state they need to undo and redo. */
interface UndoableCommand {
    val label: String
    /** Approximate memory held by this command, used to bound history size. */
    val sizeBytes: Long
    fun undo()
    fun redo()
    /** Called when the command is evicted from history so it can release resources. */
    fun dispose() {}
}

/**
 * Linear undo/redo history bounded by entry count and total memory. The oldest entries are
 * dropped first when a limit is exceeded.
 */
class UndoStack(
    private val maxEntries: Int = 100,
    private val maxBytes: Long = 192L * 1024 * 1024,
) {
    private val undoList = ArrayDeque<UndoableCommand>()
    private val redoList = ArrayDeque<UndoableCommand>()
    private var listener: (() -> Unit)? = null

    val canUndo: Boolean get() = undoList.isNotEmpty()
    val canRedo: Boolean get() = redoList.isNotEmpty()
    val undoCount: Int get() = undoList.size
    val redoCount: Int get() = redoList.size
    val totalBytes: Long get() = undoList.sumOf { it.sizeBytes } + redoList.sumOf { it.sizeBytes }

    fun setOnChangedListener(l: (() -> Unit)?) {
        listener = l
    }

    /** Records an already-applied command. Clears the redo branch. */
    fun push(command: UndoableCommand) {
        redoList.forEach { it.dispose() }
        redoList.clear()
        undoList.addLast(command)
        trim()
        listener?.invoke()
    }

    fun undo(): UndoableCommand? {
        val cmd = undoList.removeLastOrNull() ?: return null
        cmd.undo()
        redoList.addLast(cmd)
        listener?.invoke()
        return cmd
    }

    fun redo(): UndoableCommand? {
        val cmd = redoList.removeLastOrNull() ?: return null
        cmd.redo()
        undoList.addLast(cmd)
        listener?.invoke()
        return cmd
    }

    fun clear() {
        undoList.forEach { it.dispose() }
        redoList.forEach { it.dispose() }
        undoList.clear()
        redoList.clear()
        listener?.invoke()
    }

    private fun trim() {
        while (undoList.size > maxEntries || (undoList.size > 1 && totalBytes > maxBytes)) {
            undoList.removeFirst().dispose()
        }
    }
}
