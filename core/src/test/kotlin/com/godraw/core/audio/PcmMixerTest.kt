package com.godraw.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmMixerTest {

    @Test
    fun convertMonoToStereoAndResample() {
        val mono = PcmAudio(ShortArray(22050) { 1000 }, 22050, 1)
        val out = PcmMixer.convert(mono)
        assertEquals(44100, out.sampleRate)
        assertEquals(2, out.channels)
        assertEquals(44100, out.frameCount)
        assertEquals(1000.toShort(), out.samples[100])
        assertEquals(1000.toShort(), out.samples[101])
    }

    @Test
    fun mixPlacesClipAtOffsetWithTrimAndVolume() {
        val audio = PcmAudio(ShortArray(44100 * 2) { 10000 }, 44100, 2) // 1 s stereo
        val clip = MixClip(audio, startMs = 500, trimStartMs = 0, trimEndMs = 250, volume = 0.5f)
        val mixed = PcmMixer.mix(listOf(clip), durationMs = 1000)
        assertEquals(44100 * 2, mixed.size)
        assertEquals(0.toShort(), mixed[0])
        val inside = (44100 * 0.6).toInt() * 2
        assertEquals(5000.toShort(), mixed[inside])
        val after = (44100 * 0.8).toInt() * 2
        assertEquals(0.toShort(), mixed[after])
    }

    @Test
    fun overlappingClipsAreLimitedNotWrapped() {
        val loud = PcmAudio(ShortArray(4410 * 2) { 30000 }, 44100, 2)
        val clips = List(3) { MixClip(loud, 0, 0, 100, 1f) }
        val mixed = PcmMixer.mix(clips, 100)
        assertTrue(mixed.all { it > 0 })
        assertTrue(mixed.maxOrNull()!! <= Short.MAX_VALUE)
    }
}
