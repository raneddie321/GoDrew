package com.godraw.app.engine

import android.graphics.Bitmap
import com.godraw.core.effects.EffectProcessor
import com.godraw.core.effects.EffectType
import com.godraw.core.entitlement.Access
import com.godraw.core.model.CellKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live effect preview and application for the active layer. */
class EffectController(private val engine: DrawingEngine) {

    private var previewJob: Job? = null
    private var sourceKey: CellKey? = null
    var currentType: EffectType? = null
        private set
    var currentParams: Map<String, Float> = emptyMap()
        private set

    fun canUse(type: EffectType): Access = engine.gate().canUseEffect(type.ordinal)

    /** Recomputes the preview after a short debounce so sliders stay responsive. */
    fun preview(type: EffectType, params: Map<String, Float>) {
        val access = canUse(type)
        if (access is Access.Locked) {
            engine.emit(EngineEvent.Locked(access))
            return
        }
        val key = engine.activeKey() ?: return
        if (key.isMask) return
        val source = engine.store.get(key) ?: return
        currentType = type
        currentParams = params
        sourceKey = key
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        val mask = engine.selection?.mask(w, h)
        previewJob?.cancel()
        previewJob = engine.scope.launch {
            delay(150)
            engine.updateState { it.copy(busy = true) }
            val result = withContext(Dispatchers.Default) { process(type, params, pixels, mask, w, h) }
            engine.updateState { it.copy(busy = false) }
            engine.effectPreview = Bitmap.createBitmap(result, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
            engine.publish()
            engine.invalidate()
        }
    }

    private fun process(type: EffectType, params: Map<String, Float>, pixels: IntArray, mask: ByteArray?, w: Int, h: Int): IntArray {
        val out = EffectProcessor.apply(type, pixels, w, h, params)
        if (mask != null) {
            // Blend by selection coverage so only selected pixels change.
            for (i in out.indices) {
                val m = mask[i].toInt() and 0xFF
                if (m == 255) continue
                out[i] = if (m == 0) pixels[i] else lerpColor(pixels[i], out[i], m / 255f)
            }
        }
        return out
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + ((((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t)).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun cancel() {
        previewJob?.cancel()
        engine.effectPreview = null
        currentType = null
        engine.updateState { it.copy(busy = false) }
        engine.publish()
        engine.invalidate()
    }

    /** Applies the effect to the current frame, or to every frame of the layer. */
    fun apply(allFrames: Boolean) {
        val type = currentType ?: return
        val params = currentParams
        val key = sourceKey ?: return
        val layer = engine.doc.layer(key.layerId) ?: return
        previewJob?.cancel()
        engine.snapshots.beginOperation()
        if (!allFrames || layer.isStatic) {
            val preview = engine.effectPreview
            val target = engine.store.get(key)
            if (preview == null || target == null) {
                cancel()
                return
            }
            val before = mapOf(key to engine.snapshots.capture(key))
            engine.store.set(key, preview)
            engine.effectPreview = null
            currentType = null
            engine.commitDocChange("effect", engine.doc, before, mapOf(key to engine.snapshots.capture(key)))
            return
        }
        val mask = engine.selection?.mask(engine.doc.width, engine.doc.height)
        val frames = engine.doc.frames
        engine.effectPreview = null
        currentType = null
        engine.updateState { it.copy(busy = true, progress = 0f) }
        engine.scope.launch {
            val before = HashMap<CellKey, CellSnapshot>()
            val after = HashMap<CellKey, CellSnapshot>()
            frames.forEachIndexed { i, frame ->
                val k = CellKey(frame.id, layer.id)
                val src = engine.store.get(k)
                if (src != null) {
                    val w = src.width
                    val h = src.height
                    val pixels = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
                    val out = withContext(Dispatchers.Default) { process(type, params, pixels, mask, w, h) }
                    before[k] = engine.snapshots.capture(k)
                    val result = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
                    engine.store.set(k, result)
                    after[k] = engine.snapshots.capture(k)
                }
                engine.updateState { it.copy(progress = (i + 1f) / frames.size) }
            }
            engine.updateState { it.copy(busy = false, progress = null) }
            engine.commitDocChange("effect_all", engine.doc, before, after)
        }
    }
}
