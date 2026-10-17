package com.godraw.app.engine

import android.graphics.Bitmap
import com.godraw.core.animation.EasingType
import com.godraw.core.animation.KeyframeSampler
import com.godraw.core.animation.TransitionPreset
import com.godraw.core.animation.Transitions
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.model.AudioClip
import com.godraw.core.model.CellKey
import com.godraw.core.model.Frame
import com.godraw.core.model.GuideSettings
import com.godraw.core.model.Keyframe
import com.godraw.core.model.LayerAnimation
import com.godraw.core.model.MotionPathMode
import com.godraw.core.model.OnionSkinSettings
import com.godraw.core.model.SymmetryMode
import com.godraw.core.model.TransformProperty
import com.godraw.core.timeline.FrameOps

// Frames, keyframes, audio clips and guides for DrawingEngine.

private fun DrawingEngine.lockedBy(access: Access, feature: Feature? = null): Boolean {
    if (access is Access.Locked) {
        emit(EngineEvent.Locked(access, feature))
        return true
    }
    return false
}

/** Frame-specific (non-static) raster layers, whose cells follow their frames. */
private fun DrawingEngine.animatedLayers() = doc.layers.filter { !it.isGroup && !it.isStatic }

fun DrawingEngine.setActiveFrame(index: Int) {
    if (index == doc.activeFrameIndex || index !in doc.frames.indices) return
    if (floating != null) commitFloating()
    setDocQuiet(doc.copy(activeFrameIndex = index))
}

fun DrawingEngine.addFrame() {
    if (lockedBy(gate().canAddFrames(doc.frames.size))) return
    if (floating != null) commitFloating()
    val r = FrameOps.insertBlank(doc.frames, doc.activeFrameIndex)
    commitDocChange("add_frame", doc.copy(frames = r.frames, activeFrameIndex = r.activeIndex))
}

private fun DrawingEngine.copyCells(copies: List<Pair<String, String>>, before: MutableMap<CellKey, CellSnapshot>, after: MutableMap<CellKey, CellSnapshot>) {
    for ((srcFrame, dstFrame) in copies) {
        for (layer in animatedLayers()) {
            for (mask in listOf(false, true)) {
                val src = store.get(CellKey(srcFrame, layer.id, mask)) ?: continue
                val dstKey = CellKey(dstFrame, layer.id, mask)
                store.set(dstKey, src.copy(Bitmap.Config.ARGB_8888, true))
                before[dstKey] = CellSnapshot.Empty
                after[dstKey] = snapshots.capture(dstKey)
            }
        }
    }
}

fun DrawingEngine.duplicateFrames(indices: List<Int>) {
    if (indices.isEmpty()) return
    if (lockedBy(gate().canAddFrames(doc.frames.size, indices.size))) return
    if (floating != null) commitFloating()
    snapshots.beginOperation()
    val r = FrameOps.duplicate(doc.frames, indices)
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    copyCells(r.cellCopies, before, after)
    commitDocChange("duplicate_frame", doc.copy(frames = r.frames, activeFrameIndex = r.activeIndex), before, after)
}

fun DrawingEngine.deleteFrames(indices: List<Int>) {
    if (indices.isEmpty()) return
    if (floating != null) cancelFloating()
    snapshots.beginOperation()
    val r = FrameOps.delete(doc.frames, indices)
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    for (frameId in r.removedFrameIds) {
        for (layer in animatedLayers()) {
            for (mask in listOf(false, true)) {
                val key = CellKey(frameId, layer.id, mask)
                before[key] = snapshots.capture(key)
                after[key] = CellSnapshot.Empty
                store.set(key, null)
            }
        }
    }
    commitDocChange("delete_frame", doc.copy(frames = r.frames, activeFrameIndex = r.activeIndex), before, after)
}

fun DrawingEngine.moveFrames(indices: List<Int>, target: Int) {
    if (indices.isEmpty()) return
    val r = FrameOps.move(doc.frames, indices, target)
    if (r.frames == doc.frames) return
    commitDocChange("move_frame", doc.copy(frames = r.frames, activeFrameIndex = r.activeIndex))
}

/** In-app frame clipboard: frames with private copies of their cells. */
class FrameClipboard(val frames: List<Frame>, val cells: Map<CellKey, Bitmap>)

fun DrawingEngine.copyFrames(indices: List<Int>): FrameClipboard? {
    val frames = indices.sorted().mapNotNull { doc.frames.getOrNull(it) }
    if (frames.isEmpty()) return null
    val cells = HashMap<CellKey, Bitmap>()
    for (f in frames) for (layer in animatedLayers()) for (mask in listOf(false, true)) {
        val key = CellKey(f.id, layer.id, mask)
        store.get(key)?.let { cells[key] = it.copy(Bitmap.Config.ARGB_8888, false) }
    }
    return FrameClipboard(frames, cells)
}

fun DrawingEngine.pasteFrames(clip: FrameClipboard) {
    if (lockedBy(gate().canAddFrames(doc.frames.size, clip.frames.size))) return
    if (floating != null) commitFloating()
    snapshots.beginOperation()
    val r = FrameOps.paste(doc.frames, clip.frames, doc.activeFrameIndex)
    val before = HashMap<CellKey, CellSnapshot>()
    val after = HashMap<CellKey, CellSnapshot>()
    for ((src, dst) in r.cellCopies) {
        for ((key, bmp) in clip.cells) {
            if (key.frameId != src || doc.layer(key.layerId) == null) continue
            val dstKey = CellKey(dst, key.layerId, key.isMask)
            store.set(dstKey, bmp.copy(Bitmap.Config.ARGB_8888, true))
            before[dstKey] = CellSnapshot.Empty
            after[dstKey] = snapshots.capture(dstKey)
        }
    }
    commitDocChange("paste_frames", doc.copy(frames = r.frames, activeFrameIndex = r.activeIndex), before, after)
}

fun DrawingEngine.setFrameHold(index: Int, hold: Int) =
    commitDocChange("hold", doc.copy(frames = FrameOps.setHold(doc.frames, index, hold)))

fun DrawingEngine.setFps(fps: Int) {
    if (lockedBy(gate().canUseFps(fps))) return
    commitDocChange("fps", doc.copy(fps = fps.coerceIn(1, 60)))
}

fun DrawingEngine.setLoop(loop: Boolean) = setDocQuiet(doc.copy(loop = loop))

fun DrawingEngine.setOnionSkin(settings: OnionSkinSettings) =
    setDocQuiet(doc.copy(onionSkin = settings.copy(framesBefore = settings.framesBefore.coerceIn(0, 5), framesAfter = settings.framesAfter.coerceIn(0, 5))))

// ---------------------------------------------------------------------------------- keyframes

private fun DrawingEngine.updateAnimation(layerId: String, label: String, transform: (LayerAnimation) -> LayerAnimation) {
    val layer = doc.layer(layerId) ?: return
    commitDocChange(label, doc.copy(layers = doc.layers.map { if (it.id == layerId) it.copy(animation = transform(layer.animation)) else it }))
}

private fun DrawingEngine.easingAllowed(easing: EasingType): Boolean {
    val feature = easing.requiredFeature ?: return true
    return !lockedBy(gate().check(feature), feature)
}

/** Sets (or replaces) a keyframe at the current frame. */
fun DrawingEngine.setKeyframe(layerId: String, property: TransformProperty, value: Float, easing: EasingType? = null) {
    val frame = doc.activeFrameIndex
    val layer = doc.layer(layerId) ?: return
    val existing = layer.animation.track(property).firstOrNull { it.frame == frame }
    val chosen = easing ?: existing?.easing ?: EasingType.EASE_IN_OUT
    if (!easingAllowed(chosen)) return
    updateAnimation(layerId, "keyframe") { a ->
        a.withTrack(property, KeyframeSampler.upsert(a.track(property), Keyframe(frame, value, chosen, existing?.bezier)))
    }
}

/** Keys every transform property at the current frame with its current value. */
fun DrawingEngine.keyAllProperties(layerId: String) {
    val layer = doc.layer(layerId) ?: return
    val frame = doc.activeFrameIndex
    val t = KeyframeSampler.sample(layer.animation, frame.toFloat())
    val values = mapOf(
        TransformProperty.POSITION_X to t.x,
        TransformProperty.POSITION_Y to t.y,
        TransformProperty.ROTATION to t.rotation,
        TransformProperty.SCALE to t.scale,
        TransformProperty.OPACITY to t.opacity,
    )
    updateAnimation(layerId, "key_all") { a ->
        values.entries.fold(a) { acc, (p, v) ->
            val existing = acc.track(p).firstOrNull { it.frame == frame }
            acc.withTrack(p, KeyframeSampler.upsert(acc.track(p), Keyframe(frame, v, existing?.easing ?: EasingType.EASE_IN_OUT, existing?.bezier)))
        }
    }
}

fun DrawingEngine.removeKeyframe(layerId: String, property: TransformProperty, frame: Int) =
    updateAnimation(layerId, "remove_keyframe") { a -> a.withTrack(property, a.track(property).filterNot { it.frame == frame }) }

fun DrawingEngine.setKeyframeEasing(layerId: String, property: TransformProperty, frame: Int, easing: EasingType, bezier: List<Float>? = null) {
    if (!easingAllowed(easing)) return
    updateAnimation(layerId, "easing") { a ->
        a.withTrack(property, a.track(property).map { if (it.frame == frame) it.copy(easing = easing, bezier = bezier) else it })
    }
}

/** Moves a keyframe in time (drag on the keyframe row). */
fun DrawingEngine.moveKeyframe(layerId: String, property: TransformProperty, from: Int, to: Int) {
    if (from == to) return
    updateAnimation(layerId, "move_keyframe") { a ->
        val kf = a.track(property).firstOrNull { it.frame == from } ?: return@updateAnimation a
        a.withTrack(property, KeyframeSampler.upsert(a.track(property).filterNot { it.frame == from }, kf.copy(frame = to.coerceAtLeast(0))))
    }
}

fun DrawingEngine.setMotionPathMode(layerId: String, mode: MotionPathMode) {
    if (mode == MotionPathMode.SMOOTH && lockedBy(gate().check(Feature.SMOOTH_MOTION_PATHS), Feature.SMOOTH_MOTION_PATHS)) return
    updateAnimation(layerId, "path_mode") { it.copy(pathMode = mode) }
}

fun DrawingEngine.applyTransition(layerId: String, preset: TransitionPreset, length: Int) {
    preset.requiredFeature?.let { f -> if (lockedBy(gate().check(f), f)) return }
    updateAnimation(layerId, "transition") { a ->
        Transitions.apply(a, preset, doc.activeFrameIndex, length, doc.width, doc.height)
    }
}

fun DrawingEngine.copyAnimation(layerId: String): LayerAnimation? {
    if (lockedBy(gate().check(Feature.KEYFRAME_COPY_PASTE), Feature.KEYFRAME_COPY_PASTE)) return null
    return doc.layer(layerId)?.animation
}

fun DrawingEngine.pasteAnimation(layerId: String, animation: LayerAnimation) {
    if (lockedBy(gate().check(Feature.KEYFRAME_COPY_PASTE), Feature.KEYFRAME_COPY_PASTE)) return
    updateAnimation(layerId, "paste_animation") { animation }
}

// ---------------------------------------------------------------------------------- audio

fun DrawingEngine.addAudioClip(clip: AudioClip): Boolean {
    if (doc.audioClips.isNotEmpty()) {
        val access = gate().check(Feature.MULTIPLE_AUDIO_CLIPS)
        if (lockedBy(access, Feature.MULTIPLE_AUDIO_CLIPS)) return false
    }
    if (lockedBy(gate().canAddAudioClip(doc.audioClips.size))) return false
    commitDocChange("add_audio", doc.copy(audioClips = doc.audioClips + clip))
    return true
}

fun DrawingEngine.updateAudioClip(clip: AudioClip) =
    commitDocChange("edit_audio", doc.copy(audioClips = doc.audioClips.map { if (it.id == clip.id) clip else it }))

fun DrawingEngine.removeAudioClip(id: String) =
    commitDocChange("remove_audio", doc.copy(audioClips = doc.audioClips.filterNot { it.id == id }))

// ---------------------------------------------------------------------------------- guides

fun DrawingEngine.setGuides(guides: GuideSettings) {
    if (guides.symmetry == SymmetryMode.RADIAL && lockedBy(gate().check(Feature.RADIAL_SYMMETRY), Feature.RADIAL_SYMMETRY)) return
    if (guides.perspectiveEnabled && lockedBy(gate().check(Feature.PERSPECTIVE_GUIDES), Feature.PERSPECTIVE_GUIDES)) return
    setDocQuiet(doc.copy(guides = guides))
}
