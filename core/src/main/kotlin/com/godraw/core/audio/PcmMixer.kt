package com.godraw.core.audio

import kotlin.math.roundToInt

/** Decoded 16-bit PCM audio, interleaved. */
class PcmAudio(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
    val frameCount: Int get() = if (channels == 0) 0 else samples.size / channels
    val durationMs: Long get() = frameCount * 1000L / sampleRate.coerceAtLeast(1)
}

/** A clip placed on the timeline for mixing. */
data class MixClip(
    val audio: PcmAudio,
    val startMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val volume: Float,
)

/**
 * Converts audio to a common format and mixes timeline clips. Used by MP4 export so any number of
 * clips with different sample rates and channel counts end up in one AAC track.
 */
object PcmMixer {

    const val OUTPUT_RATE = 44100
    const val OUTPUT_CHANNELS = 2

    /** Linear-interpolation resampling to [targetRate] with up/down-mixing to [targetChannels]. */
    fun convert(audio: PcmAudio, targetRate: Int = OUTPUT_RATE, targetChannels: Int = OUTPUT_CHANNELS): PcmAudio {
        val srcFrames = audio.frameCount
        if (srcFrames == 0) return PcmAudio(ShortArray(0), targetRate, targetChannels)
        val outFrames = (srcFrames.toLong() * targetRate / audio.sampleRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outFrames * targetChannels)
        val ratio = audio.sampleRate.toDouble() / targetRate
        for (f in 0 until outFrames) {
            val pos = f * ratio
            val i0 = pos.toInt().coerceAtMost(srcFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
            val t = (pos - i0).toFloat()
            for (c in 0 until targetChannels) {
                val v0 = channelValue(audio, i0, c, targetChannels)
                val v1 = channelValue(audio, i1, c, targetChannels)
                out[f * targetChannels + c] = (v0 + (v1 - v0) * t).roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return PcmAudio(out, targetRate, targetChannels)
    }

    private fun channelValue(audio: PcmAudio, frame: Int, channel: Int, targetChannels: Int): Float {
        val ch = audio.channels
        val base = frame * ch
        return when {
            ch == targetChannels -> audio.samples[base + channel].toFloat()
            ch == 1 -> audio.samples[base].toFloat()
            targetChannels == 1 -> {
                var sum = 0f
                for (c in 0 until ch) sum += audio.samples[base + c]
                sum / ch
            }
            else -> audio.samples[base + (channel % ch)].toFloat()
        }
    }

    /** Mixes [clips] into a buffer of [durationMs] at the output format, with soft clipping. */
    fun mix(clips: List<MixClip>, durationMs: Long): ShortArray {
        val totalFrames = (durationMs * OUTPUT_RATE / 1000).toInt().coerceAtLeast(0)
        val acc = FloatArray(totalFrames * OUTPUT_CHANNELS)
        for (clip in clips) {
            val audio = convert(clip.audio)
            val srcStart = (clip.trimStartMs * OUTPUT_RATE / 1000).toInt().coerceIn(0, audio.frameCount)
            val srcEnd = (clip.trimEndMs * OUTPUT_RATE / 1000).toInt().coerceIn(srcStart, audio.frameCount)
            val dstStart = (clip.startMs * OUTPUT_RATE / 1000).toInt()
            for (f in srcStart until srcEnd) {
                val dst = dstStart + (f - srcStart)
                if (dst < 0) continue
                if (dst >= totalFrames) break
                for (c in 0 until OUTPUT_CHANNELS) {
                    acc[dst * OUTPUT_CHANNELS + c] += audio.samples[f * OUTPUT_CHANNELS + c] * clip.volume
                }
            }
        }
        return ShortArray(acc.size) { i -> softClip(acc[i]) }
    }

    /** Leaves normal levels untouched and smoothly limits peaks above 90% full scale. */
    private fun softClip(v: Float): Short {
        val limit = 32767f
        val x = v / limit
        val ax = kotlin.math.abs(x)
        val y = if (ax <= 0.9f) x else {
            val s = if (x > 0) 1f else -1f
            s * (0.9f + 0.1f * kotlin.math.tanh((ax - 0.9f) / 0.1f))
        }
        return (y * limit).roundToInt().coerceIn(-32768, 32767).toShort()
    }
}
