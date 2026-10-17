package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.godraw.core.animation.KeyframeSampler
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.model.CellKey
import com.godraw.core.model.Ids
import com.godraw.core.model.Layer
import com.godraw.core.model.LayerAnimation
import com.godraw.core.model.LayerBlendMode
import com.godraw.core.model.LayerKind
import com.godraw.core.model.ProjectDocument

// Layer management for DrawingEngine. Every structural change is one undoable step and goes
// through the FeatureGate, so locked features cannot be reached from any UI path.

private fun DrawingEngine.locked(access: Access, feature: Feature? = null): Boolean {
    if (access is Access.Locked) {
        emit(EngineEvent.Locked(access, feature))
        return true
    }
    return false
}

private fun DrawingEngine.requireFeature(feature: Feature): Boolean = !locked(gate().check(feature), feature)

/** All cell keys (pixels and mask) belonging to a raster layer. */
internal fun DrawingEngine.cellKeys(layer: Layer, d: ProjectDocument = doc, includeMask: Boolean = true): List<CellKey> {
    if (layer.isGroup) return emptyList()
    val frameIds = if (layer.isStatic) listOf(ProjectDocument.STATIC_FRAME_ID) else d.frames.map { it.id }
    return frameIds.flatMap { f ->
        if (includeMask) listOf(CellKey(f, layer.id), CellKey(f, layer.id, isMask = true)) else listOf(CellKey(f, layer.id))
    }
}

private fun DrawingEngine.nextLayerNumber(): Int = doc.rasterLayerCount + 1

fun DrawingEngine.selectLayer(id: String) {
    val layer = doc.layer(id) ?: return
    if (floating != null) commitFloating()
    if (layer.isGroup) {
        // Selecting a group selects its top child for drawing.
        val child = doc.children(id).lastOrNull() ?: return setDocQuiet(doc)
        setDocQuiet(doc.copy(activeLayerId = child.id))
    } else {
        setDocQuiet(doc.copy(activeLayerId = id))
    }
    if (doc.layer(doc.activeLayerId)?.hasMask != true) setEditingMask(false)
}

fun DrawingEngine.addLayer() {
    if (locked(gate().canAddLayers(doc.rasterLayerCount))) return
    val active = doc.layer(doc.activeLayerId)
    val layer = Layer(id = Ids.newId(), name = strings.layerName(nextLayerNumber()), parentId = active?.parentId)
    val index = (doc.layerIndex(doc.activeLayerId) + 1).coerceIn(0, doc.layers.size)
    val layers = doc.layers.toMutableList().apply { add(index, layer) }
    commitDocChange("add_layer", doc.copy(layers = layers, activeLayerId = layer.id))
}

/** Wraps the active layer in a new group (groups are one level deep). */
fun DrawingEngine.groupActiveLayer() {
    if (!requireFeature(Feature.LAYER_GROUPS)) return
    val active = doc.layer(doc.activeLayerId) ?: return
    if (active.parentId != null) return
    val groupCount = doc.layers.count { it.isGroup }
    val group = Layer(id = Ids.newId(), name = strings.groupName(groupCount + 1), kind = LayerKind.GROUP)
    val index = doc.layerIndex(active.id)
    val layers = doc.layers.toMutableList()
    layers[index] = active.copy(parentId = group.id, clipToBelow = false)
    layers.add(index + 1, group)
    commitDocChange("group", doc.copy(layers = layers))
}

fun DrawingEngine.moveIntoGroup(layerId: String, groupId: String?) {
    val layer = doc.layer(layerId) ?: return
    if (layer.isGroup) return
    if (groupId != null) {
        if (!requireFeature(Feature.LAYER_GROUPS)) return
        if (doc.layer(groupId)?.isGroup != true) return
    }
    val without = doc.layers.filterNot { it.id == layerId }.toMutableList()
    val moved = layer.copy(parentId = groupId)
    val insertAt = if (groupId != null) without.indexOfFirst { it.id == groupId } else without.size
    without.add(insertAt.coerceIn(0, without.size), moved)
    commitDocChange("move_to_group", doc.copy(layers = without))
}

fun DrawingEngine.ungroup(groupId: String) {
    val group = doc.layer(groupId) ?: return
    if (!group.isGroup) return
    val layers = doc.layers.filterNot { it.id == groupId }.map { if (it.parentId == groupId) it.copy(parentId = null) else it }
    commitDocChange("ungroup", doc.copy(layers = layers))
}

fun DrawingEngine.deleteLayer(id: String) {
    val layer = doc.layer(id) ?: return
    val removedIds = if (layer.isGroup) doc.children(id).map { it.id } + id else listOf(id)
    val remaining = doc.layers.filterNot { it.id in removedIds }
    if (remaining.none { !it.isGroup }) {
        emit(EngineEvent.Message(MessageKind.CANNOT_DELETE_LAST_LAYER))
        return
    }
    if (floating != null) cancelFloating()
    snapshots.beginOperation()
    val before = HashMap<CellKey, CellSnapshot>()
    doc.layers.filter { it.id in removedIds }.forEach { l -> cellKeys(l).forEach { before[it] = snapshots.capture(it) } }
    val after = before.keys.associateWith { CellSnapshot.Empty as CellSnapshot }
    val newActive = if (doc.activeLayerId in removedIds) {
        val idx = doc.layerIndex(id)
        remaining.filter { !it.isGroup }.let { list -> list.getOrNull((idx - 1).coerceAtLeast(0)) ?: list.last() }.id
    } else {
        doc.activeLayerId
    }
    after.forEach { (k, _) -> store.set(k, null) }
    commitDocChange("delete_layer", doc.copy(layers = remaining, activeLayerId = newActive), before, after)
}

fun DrawingEngine.duplicateLayer(id: String) {
    val layer = doc.layer(id) ?: return
    if (layer.isGroup) return
    if (locked(gate().canAddLayers(doc.rasterLayerCount))) return
    if (floating != null) commitFloating()
    snapshots.beginOperation()
    val copy = layer.copy(id = Ids.newId(), name = strings.copyOf(layer.name), isBackground = false, locked = false)
    val after = HashMap<CellKey, CellSnapshot>()
    val before = HashMap<CellKey, CellSnapshot>()
    for (key in cellKeys(layer)) {
        val dstKey = key.copy(layerId = copy.id)
        if (!copyCell(key, dstKey)) continue
        before[dstKey] = CellSnapshot.Empty
        after[dstKey] = snapshots.capture(dstKey)
    }
    // A copied background layer keeps its color as real pixels.
    if (layer.isBackground && doc.backgroundColor != 0) {
        for (key in cellKeys(layer, includeMask = false)) {
            val dstKey = key.copy(layerId = copy.id)
            val filled = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
            filled.eraseColor(doc.backgroundColor)
            store.get(dstKey)?.let { Canvas(filled).drawBitmap(it, 0f, 0f, null) }
            store.set(dstKey, filled)
            before[dstKey] = CellSnapshot.Empty
            after[dstKey] = snapshots.capture(dstKey)
        }
    }
    val index = doc.layerIndex(id) + 1
    val layers = doc.layers.toMutableList().apply { add(index, copy) }
    commitDocChange("duplicate_layer", doc.copy(layers = layers, activeLayerId = copy.id), before, after)
}

fun DrawingEngine.renameLayer(id: String, name: String) {
    val clean = name.trim().take(60)
    if (clean.isEmpty()) return
    commitDocChange("rename_layer", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(name = clean) else it }))
}

fun DrawingEngine.setLayerVisible(id: String, visible: Boolean) =
    commitDocChange("visibility", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(visible = visible) else it }))

fun DrawingEngine.setLayerLocked(id: String, locked: Boolean) =
    commitDocChange("lock", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(locked = locked) else it }))

/** Live opacity preview; call [DrawingEngine.endContinuousEdit] when the slider is released. */
fun DrawingEngine.previewLayerOpacity(id: String, opacity: Float) =
    updateContinuous { d -> d.copy(layers = d.layers.map { if (it.id == id) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it }) }

fun DrawingEngine.setBlendMode(id: String, mode: LayerBlendMode) {
    if (locked(gate().canUseBlendMode(mode))) return
    commitDocChange("blend", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(blendMode = mode) else it }))
}

fun DrawingEngine.setClipping(id: String, clip: Boolean) {
    if (clip && !requireFeature(Feature.CLIPPING_MASKS)) return
    val layer = doc.layer(id) ?: return
    if (layer.isGroup || layer.isBackground) return
    commitDocChange("clipping", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(clipToBelow = clip) else it }))
}

fun DrawingEngine.setLayerStatic(id: String, static: Boolean) {
    val layer = doc.layer(id) ?: return
    if (layer.isGroup || layer.isStatic == static) return
    snapshots.beginOperation()
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    val updated = layer.copy(isStatic = static)
    if (static) {
        // Keep the drawing of the current frame as the shared drawing.
        val currentKey = CellKey(doc.frames[doc.activeFrameIndex].id, layer.id)
        val staticKey = CellKey(ProjectDocument.STATIC_FRAME_ID, layer.id)
        cellKeys(layer).forEach { before[it] = snapshots.capture(it) }
        before[staticKey] = snapshots.capture(staticKey)
        val src = store.get(currentKey)?.copy(Bitmap.Config.ARGB_8888, true)
        store.set(staticKey, src)
        after[staticKey] = snapshots.capture(staticKey)
    } else {
        val staticKey = CellKey(ProjectDocument.STATIC_FRAME_ID, layer.id)
        before[staticKey] = snapshots.capture(staticKey)
        val src = store.get(staticKey)
        for (frame in doc.frames) {
            val key = CellKey(frame.id, layer.id)
            before[key] = snapshots.capture(key)
            store.set(key, src?.copy(Bitmap.Config.ARGB_8888, true))
            after[key] = snapshots.capture(key)
        }
    }
    commitDocChange("static", doc.copy(layers = doc.layers.map { if (it.id == id) updated else it }), before, after)
}

/** Moves a layer among its siblings; [toSiblingIndex] counts from the bottom. */
fun DrawingEngine.moveLayer(id: String, toSiblingIndex: Int) {
    val layer = doc.layer(id) ?: return
    val siblings = doc.layers.filter { it.parentId == layer.parentId }
    val from = siblings.indexOfFirst { it.id == id }
    val to = toSiblingIndex.coerceIn(0, siblings.lastIndex)
    if (from < 0 || from == to) return
    val reordered = siblings.toMutableList().apply { add(to, removeAt(from)) }
    // Write the new sibling order back into the sibling slots of the flat list.
    val slots = doc.layers.indices.filter { doc.layers[it].parentId == layer.parentId }
    val layers = doc.layers.toMutableList()
    slots.forEachIndexed { i, slot -> layers[slot] = reordered[i] }
    // Groups must stay above their children in the flat list; re-insert groups after children.
    commitDocChange("reorder", doc.copy(layers = normalizeGroups(layers)))
}

private fun normalizeGroups(layers: List<Layer>): List<Layer> {
    val result = ArrayList<Layer>(layers.size)
    for (l in layers) {
        if (l.parentId != null) continue
        if (l.isGroup) result += layers.filter { it.parentId == l.id }
        result += l
    }
    return result
}

/** Merges a layer into the sibling directly below it, for every frame. */
fun DrawingEngine.mergeDown(id: String) {
    val upper = doc.layer(id) ?: return
    val siblings = doc.layers.filter { it.parentId == upper.parentId }
    val idx = siblings.indexOfFirst { it.id == id }
    val lower = siblings.getOrNull(idx - 1)
    if (upper.isGroup || lower == null || lower.isGroup || lower.isStatic != upper.isStatic || lower.locked) {
        emit(EngineEvent.Message(MessageKind.MERGE_NOT_POSSIBLE))
        return
    }
    if (floating != null) commitFloating()
    snapshots.beginOperation()
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    val frameIndices = if (upper.isStatic) listOf(doc.activeFrameIndex) else doc.frames.indices.toList()
    val single = doc.copy(layers = doc.layers.map { if (it.id == upper.id) it.copy(visible = true, parentId = null, clipToBelow = false) else it })
    for (f in frameIndices) {
        val frameId = doc.frames[f].id
        val lowerKey = CellKey.forLayer(lower, frameId)
        val upperKeys = listOf(CellKey.forLayer(upper, frameId), CellKey.forLayer(upper, frameId, isMask = true))
        if (store.get(upperKeys[0]) == null) continue
        if (lowerKey !in before) before[lowerKey] = snapshots.capture(lowerKey)
        upperKeys.forEach { if (it !in before) before[it] = snapshots.capture(it) }
        val target = store.getOrCreate(lowerKey)
        val canvas = Canvas(target)
        if (lower.animation.isAnimated) {
            val inv = Matrix()
            compositor.layerMatrix(KeyframeSampler.sample(lower.animation, f.toFloat()), doc).invert(inv)
            canvas.concat(inv)
        }
        compositor.drawSingleLayer(canvas, single, single.layer(upper.id)!!, f, cellSource)
        store.markDirty(lowerKey)
        after[lowerKey] = snapshots.capture(lowerKey)
        upperKeys.forEach { after[it] = CellSnapshot.Empty }
    }
    after.filterValues { it == CellSnapshot.Empty }.keys.forEach { store.set(it, null) }
    val layers = doc.layers.filterNot { it.id == upper.id }
    commitDocChange("merge_down", doc.copy(layers = layers, activeLayerId = lower.id), before, after)
}

/** Flattens all visible layers into one layer per frame. */
fun DrawingEngine.flatten() {
    if (floating != null) commitFloating()
    snapshots.beginOperation()
    val result = Layer(id = Ids.newId(), name = strings.flattenedName, isStatic = doc.layers.all { it.isGroup || it.isStatic })
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    val frameIndices = if (result.isStatic) listOf(doc.activeFrameIndex) else doc.frames.indices.toList()
    for (f in frameIndices) {
        val key = CellKey.forLayer(result, doc.frames[f].id)
        val bmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        compositor.drawFrame(Canvas(bmp), doc, f, cellSource)
        store.set(key, bmp)
        before[key] = CellSnapshot.Empty
        after[key] = snapshots.capture(key)
    }
    for (layer in doc.layers) {
        for (key in cellKeys(layer)) {
            before[key] = snapshots.capture(key)
            after[key] = CellSnapshot.Empty
            store.set(key, null)
        }
    }
    commitDocChange("flatten", doc.copy(layers = listOf(result), activeLayerId = result.id), before, after)
}

// ---------------------------------------------------------------------------------- masks

fun DrawingEngine.addMask(id: String) {
    if (!requireFeature(Feature.LAYER_MASKS)) return
    val layer = doc.layer(id) ?: return
    if (layer.isGroup || layer.hasMask) return
    commitDocChange("add_mask", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(hasMask = true, maskEnabled = true) else it }))
    setEditingMask(true)
}

fun DrawingEngine.setMaskEnabled(id: String, enabled: Boolean) =
    commitDocChange("mask_toggle", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(maskEnabled = enabled) else it }))

/** Deletes the mask (apply = false) or bakes it into the layer pixels (apply = true). */
fun DrawingEngine.removeMask(id: String, apply: Boolean) {
    val layer = doc.layer(id) ?: return
    if (!layer.hasMask) return
    setEditingMask(false)
    snapshots.beginOperation()
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    for (key in cellKeys(layer, includeMask = false)) {
        val maskKey = key.copy(isMask = true)
        val mask = store.get(maskKey)
        before[maskKey] = snapshots.capture(maskKey)
        if (apply && mask != null) {
            val pixels = store.get(key)
            if (pixels != null) {
                before[key] = snapshots.capture(key)
                Canvas(pixels).drawBitmap(mask, 0f, 0f, Paint().apply { blendMode = BlendMode.DST_IN })
                store.markDirty(key)
                after[key] = snapshots.capture(key)
            }
        }
        after[maskKey] = CellSnapshot.Empty
        store.set(maskKey, null)
    }
    commitDocChange("remove_mask", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(hasMask = false) else it }), before, after)
}

fun DrawingEngine.clearLayerAnimation(id: String) =
    commitDocChange("clear_animation", doc.copy(layers = doc.layers.map { if (it.id == id) it.copy(animation = LayerAnimation()) else it }))
