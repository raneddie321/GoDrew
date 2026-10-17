package com.godraw.app.export

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException

/**
 * H.264 MP4 writer using MediaCodec (YUV420 flexible input via [Image] planes, explicit
 * presentation timestamps) and MediaMuxer, with an optional pre-encoded AAC track.
 */
class Mp4Encoder(
    private val output: File,
    val width: Int,
    val height: Int,
    private val fps: Int,
    private val bitrate: Int,
) {
    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TIMEOUT_US = 10_000L

        /** Largest size ≤ the request (same aspect, even, 16-aligned when needed) the device encoder supports. */
        fun supportedSize(width: Int, height: Int): Pair<Int, Int>? {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = list.codecInfos.filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, true) } }
            if (infos.isEmpty()) return null
            var w = width
            var h = height
            repeat(40) {
                val ew = (w / 2) * 2
                val eh = (h / 2) * 2
                if (ew >= 16 && eh >= 16 && infos.any { info ->
                        runCatching { info.getCapabilitiesForType(MIME).videoCapabilities.isSizeSupported(ew, eh) }.getOrDefault(false)
                    }
                ) {
                    return ew to eh
                }
                w = (w * 0.9).toInt()
                h = (h * 0.9).toInt()
            }
            return null
        }
    }

    /**
     * Encodes [frameCount] frames. [frame] returns the ARGB bitmap (width × height) for a frame
     * index; consecutive identical bitmaps may be returned for held frames.
     */
    fun encode(frameCount: Int, audio: EncodedTrack?, frame: (Int) -> Bitmap, progress: (Float) -> Unit) {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoderName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
        val codec = if (encoderName != null) MediaCodec.createByCodecName(encoderName) else MediaCodec.createEncoderByType(MIME)
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var videoTrack = -1
        val info = MediaCodec.BufferInfo()
        val argb = IntArray(width * height)
        var lastBitmap: Bitmap? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            var submitted = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val pts = submitted.toLong() * 1_000_000L / fps
                        if (submitted >= frameCount) {
                            codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val bmp = frame(submitted)
                            if (bmp !== lastBitmap) {
                                bmp.getPixels(argb, 0, width, 0, 0, width, height)
                                lastBitmap = bmp
                            }
                            val image = codec.getInputImage(inIdx) ?: throw IOException("Encoder has no input image")
                            fillYuv(image, argb)
                            codec.queueInputBuffer(inIdx, 0, width * height * 3 / 2, pts, 0)
                            submitted++
                            progress(submitted / frameCount.toFloat())
                        }
                    }
                }
                var drain = true
                while (drain) {
                    val outIdx = codec.dequeueOutputBuffer(info, if (inputDone) TIMEOUT_US else 0)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> drain = false
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Format changed twice" }
                            videoTrack = muxer.addTrack(codec.outputFormat)
                            val audioTrack = audio?.let { muxer.addTrack(it.format) } ?: -1
                            muxer.start()
                            muxerStarted = true
                            if (audio != null && audioTrack >= 0) {
                                for ((bytes, bi) in audio.samples) {
                                    muxer.writeSampleData(audioTrack, java.nio.ByteBuffer.wrap(bytes), bi)
                                }
                            }
                        }
                        outIdx >= 0 -> {
                            val buf = codec.getOutputBuffer(outIdx)
                            if (buf != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                check(muxerStarted) { "Muxer not started" }
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                muxer.writeSampleData(videoTrack, buf, info)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                                drain = false
                            }
                        }
                        else -> drain = false
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    /** BT.601 limited-range RGB → YUV420 written according to the image's plane layout. */
    private fun fillYuv(image: Image, argb: IntArray) {
        val planes = image.planes
        val yBuf = planes[0].buffer
        val yRow = planes[0].rowStride
        val yPix = planes[0].pixelStride
        val uBuf = planes[1].buffer
        val uRow = planes[1].rowStride
        val uPix = planes[1].pixelStride
        val vBuf = planes[2].buffer
        val vRow = planes[2].rowStride
        val vPix = planes[2].pixelStride
        val w = width
        val h = height
        for (y in 0 until h) {
            val base = y * w
            val rowOff = y * yRow
            for (x in 0 until w) {
                val c = argb[base + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                yBuf.put(rowOff + x * yPix, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
            }
        }
        for (y in 0 until h / 2) {
            for (x in 0 until w / 2) {
                val i0 = (y * 2) * w + x * 2
                // Average the 2×2 block for smoother chroma.
                val c0 = argb[i0]
                val c1 = argb[i0 + 1]
                val c2 = argb[i0 + w]
                val c3 = argb[i0 + w + 1]
                var r = ((c0 shr 16) and 0xFF) + ((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF)
                var g = ((c0 shr 8) and 0xFF) + ((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF)
                var b = (c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF)
                r /= 4; g /= 4; b /= 4
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                uBuf.put(y * uRow + x * uPix, u.toByte())
                vBuf.put(y * vRow + x * vPix, v.toByte())
            }
        }
    }
}
