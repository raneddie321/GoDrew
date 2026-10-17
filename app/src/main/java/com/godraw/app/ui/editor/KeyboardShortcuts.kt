package com.godraw.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.ToolType
import com.godraw.app.engine.defaultBrushFor

/**
 * Hardware keyboard shortcuts for tablets and Chromebooks. Returns true when the event was used.
 */
fun handleEditorShortcut(event: KeyEvent, vm: EditorViewModel, engine: DrawingEngine): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val st = engine.state.value
    if (event.isCtrlPressed) {
        when (event.key) {
            Key.Z -> if (event.isShiftPressed) engine.redo() else engine.undo()
            Key.Y -> engine.redo()
            Key.S -> vm.save(manual = true)
            Key.A -> engine.selectAll()
            Key.D -> engine.deselect()
            Key.I -> engine.invertSelection()
            Key.C -> engine.copySelection()
            Key.X -> engine.copySelection(cut = true)
            Key.V -> engine.paste()
            else -> return false
        }
        return true
    }
    val tool = when (event.key) {
        Key.B -> ToolType.BRUSH
        Key.P -> ToolType.PENCIL
        Key.N -> ToolType.PEN
        Key.E -> ToolType.ERASER
        Key.G -> ToolType.FILL
        Key.I -> ToolType.EYEDROPPER
        Key.U -> ToolType.RECTANGLE
        Key.T -> ToolType.TEXT
        Key.M -> ToolType.SELECT_RECT
        Key.L -> ToolType.SELECT_LASSO
        Key.V -> ToolType.TRANSFORM
        Key.H -> ToolType.HAND
        else -> null
    }
    if (tool != null) {
        engine.setTool(tool)
        return true
    }
    when (event.key) {
        Key.LeftBracket, Key.RightBracket -> {
            val t = if (st.tool.isPainting) st.tool else ToolType.BRUSH
            val brush = st.brushes[t] ?: defaultBrushFor(t)
            val factor = if (event.key == Key.RightBracket) 1.15f else 1f / 1.15f
            engine.setBrush(t, brush.copy(size = (brush.size * factor).coerceIn(1f, 500f)))
        }
        Key.Spacebar -> if (st.doc.isAnimation) vm.playback?.toggle() else return false
        Key.Enter -> if (st.floating) engine.commitFloating() else return false
        Key.Escape -> when {
            st.floating -> engine.cancelFloating()
            st.hasSelection -> engine.deselect()
            else -> return false
        }
        Key.Delete, Key.Backspace -> engine.clearSelectionPixels()
        else -> return false
    }
    return true
}
