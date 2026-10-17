package com.godraw.app.export

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import com.godraw.core.audio.PcmAudio
import com.godraw.core.audio.PcmMixer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encoded AAC samples ready for muxing. */
class EncodedTrack(val format: MediaFormat, val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>)

object AudioCodecs {

    private const val TIMEOUT_US = 10_000L

    /** Decodes the first audio track of [file] to 16-bit PCM, stopping after [maxMs]. */
    fun decode(file: File, maxMs: Long): PcmAudio? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            var out = ShortArray(1 shl 16)
            var size = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0 || extractor.sampleTime > maxMs * 1000) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val buf = codec.getOutputBuffer(outIdx)
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            buf.order(ByteOrder.nativeOrder())
                            val samples: ShortArray = if (floatPcm) {
                                val fb = buf.asFloatBuffer()
                                ShortArray(fb.remaining()) { (fb.get(it).coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                            } else {
                                val sb = buf.asShortBuffer()
                                ShortArray(sb.remaining()).also { sb.get(it) }
                            }
                            if (size + samples.size > out.size) out = out.copyOf(maxOf(out.size * 2, size + samples.size))
                            System.arraycopy(samples, 0, out, size, samples.size)
                            size += samples.size
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            floatPcm = f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return PcmAudio(out.copyOf(size), sampleRate, channels)
        } catch (e: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    /** Encodes interleaved stereo 44.1 kHz PCM to AAC-LC. */
    fun encodeAac(pcm: ShortArray, bitrate: Int = 160_000): EncodedTrack {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, PcmMixer.OUTPUT_RATE, PcmMixer.OUTPUT_CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val samples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var outputFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf: ByteBuffer = codec.getInputBuffer(inIdx)!!
                        buf.clear()
                        val maxShorts = buf.capacity() / 2
                        val n = minOf(maxShorts, pcm.size - offset)
                        val ptsUs = offset.toLong() / PcmMixer.OUTPUT_CHANNELS * 1_000_000L / PcmMixer.OUTPUT_RATE
                        if (n <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, offset, n)
                            codec.queueInputBuffer(inIdx, 0, n * 2, ptsUs, 0)
                            offset += n
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.get(bytes)
                        val copy = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                        samples += bytes to copy
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = codec.outputFormat
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return EncodedTrack(outputFormat ?: format, samples)
    }
}
