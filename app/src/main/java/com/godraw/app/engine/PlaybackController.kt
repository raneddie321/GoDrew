package com.godraw.app.engine

import android.media.MediaPlayer
import android.view.Choreographer
import com.godraw.core.model.AudioClip
import com.godraw.core.model.CellKey
import com.godraw.core.timeline.PlaybackClock
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays the animation at the document frame rate using Choreographer vsync callbacks, keeping
 * imported audio clips in sync (seeking when drift exceeds a few frames).
 */
class PlaybackController(
    private val engine: DrawingEngine,
    private val audioFile: (AudioClip) -> File,
) : Choreographer.FrameCallback {

    private var clock: PlaybackClock? = null
    private var startNanos = 0L
    private var startOffsetUs = 0L
    private var running = false
    private var lastFrame = -1
    private val players = HashMap<String, MediaPlayer>()

    val isPlaying: Boolean get() = running

    fun toggle() = if (running) stop() else start()

    fun start() {
        val doc = engine.doc
        if (doc.frames.size < 2 && doc.audioClips.isEmpty()) return
        val c = PlaybackClock(doc.frames, doc.fps)
        clock = c
        startOffsetUs = c.ticksToUs(c.frameStartTick(doc.activeFrameIndex))
        startNanos = System.nanoTime()
        running = true
        lastFrame = -1
        engine.setPlaying(true)
        preparePlayers()
        prefetch(doc.activeFrameIndex)
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        releasePlayers()
        // Leave the editor on the frame where playback stopped.
        val shown = engine.displayFrame
        engine.setPlaying(false)
        engine.setActiveFrame(shown)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val c = clock ?: return
        val doc = engine.doc
        val elapsedUs = startOffsetUs + (System.nanoTime() - startNanos) / 1000
        if (!doc.loop && c.isFinished(elapsedUs)) {
            engine.setDisplayFrame(doc.frames.lastIndex)
            stop()
            return
        }
        val total = c.totalDurationUs.coerceAtLeast(1)
        val timelineUs = if (doc.loop) elapsedUs % total else elapsedUs
        val index = c.frameAt(elapsedUs, doc.loop)
        if (index != lastFrame) {
            if (lastFrame >= 0 && index < lastFrame) syncAudio(timelineUs / 1000, forceSeek = true)
            lastFrame = index
            engine.setDisplayFrame(index)
            prefetch(index)
        }
        syncAudio(timelineUs / 1000, forceSeek = false)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun prefetch(index: Int) {
        val doc = engine.doc
        val keys = ArrayList<CellKey>()
        for (i in 1..PREFETCH_FRAMES) {
            val f = doc.frames.getOrNull((index + i) % doc.frames.size) ?: continue
            doc.layers.filter { !it.isGroup && it.visible }.forEach { keys += CellKey.forLayer(it, f.id) }
        }
        engine.scope.launch { engine.store.prefetch(keys) }
    }

    private fun preparePlayers() {
        releasePlayers()
        for (clip in engine.doc.audioClips) {
            if (clip.muted) continue
            val file = audioFile(clip)
            if (!file.exists()) continue
            runCatching {
                val mp = MediaPlayer()
                mp.setDataSource(file.path)
                mp.prepare()
                mp.setVolume(clip.volume, clip.volume)
                players[clip.id] = mp
            }
        }
    }

    private fun syncAudio(timelineMs: Long, forceSeek: Boolean) {
        for (clip in engine.doc.audioClips) {
            val mp = players[clip.id] ?: continue
            val local = timelineMs - clip.startMs + clip.trimStartMs
            val inRange = timelineMs >= clip.startMs && local < clip.effectiveEndMs
            runCatching {
                if (inRange) {
                    if (!mp.isPlaying) {
                        mp.seekTo(local.toInt())
                        mp.start()
                    } else if (forceSeek || kotlin.math.abs(mp.currentPosition - local) > DRIFT_MS) {
                        mp.seekTo(local.toInt())
                    }
                } else if (mp.isPlaying) {
                    mp.pause()
                }
            }
        }
    }

    private fun releasePlayers() {
        players.values.forEach { runCatching { it.stop(); it.release() } }
        players.clear()
    }

    fun release() {
        if (running) stop()
        releasePlayers()
    }

    private companion object {
        const val PREFETCH_FRAMES = 6
        const val DRIFT_MS = 120
    }
}
