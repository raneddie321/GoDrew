package com.godraw.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    private class SetValue(private val target: IntArray, private val before: Int, private val after: Int, override val sizeBytes: Long = 8) : UndoableCommand {
        override val label = "set"
        var disposed = false
        override fun undo() { target[0] = before }
        override fun redo() { target[0] = after }
        override fun dispose() { disposed = true }
    }

    private fun apply(stack: UndoStack, state: IntArray, value: Int, size: Long = 8): SetValue {
        val cmd = SetValue(state, state[0], value, size)
        cmd.redo()
        stack.push(cmd)
        return cmd
    }

    @Test
    fun undoRedoRestoresState() {
        val state = intArrayOf(0)
        val stack = UndoStack()
        apply(stack, state, 1)
        apply(stack, state, 2)
        stack.undo()
        assertEquals(1, state[0])
        stack.undo()
        assertEquals(0, state[0])
        assertFalse(stack.canUndo)
        stack.redo()
        stack.redo()
        assertEquals(2, state[0])
        assertFalse(stack.canRedo)
    }

    @Test
    fun newCommandClearsRedoBranch() {
        val state = intArrayOf(0)
        val stack = UndoStack()
        apply(stack, state, 1)
        val second = apply(stack, state, 2)
        stack.undo()
        apply(stack, state, 3)
        assertFalse(stack.canRedo)
        assertTrue(second.disposed)
    }

    @Test
    fun historyIsBoundedByCountAndMemory() {
        val state = intArrayOf(0)
        val byCount = UndoStack(maxEntries = 3)
        repeat(10) { apply(byCount, state, it) }
        assertEquals(3, byCount.undoCount)

        val byBytes = UndoStack(maxEntries = 100, maxBytes = 100)
        val first = apply(byBytes, state, 1, size = 60)
        apply(byBytes, state, 2, size = 60)
        assertEquals(1, byBytes.undoCount)
        assertTrue(first.disposed)
    }
}
