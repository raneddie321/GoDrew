package com.godraw.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.content.FileProvider
import com.godraw.app.engine.CellSource
import com.godraw.app.engine.Compositor
import com.godraw.core.audio.MixClip
import com.godraw.core.audio.PcmMixer
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.gif.GifEncoder
import com.godraw.core.model.AudioClip
import com.godraw.core.model.CellKey
import com.godraw.core.model.ProjectDocument
import com.godraw.core.timeline.PlaybackClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val mime: String, val extension: String, val animated: Boolean, val feature: Feature? = null) {
    PNG("image/png", "png", false),
    PNG_TRANSPARENT("image/png", "png", false),
    JPG("image/jpeg", "jpg", false),
    GIF("image/gif", "gif", true),
    MP4("video/mp4", "mp4", true),
    PNG_SEQUENCE("application/zip", "zip", true, Feature.PNG_SEQUENCE_EXPORT),
}

data class ExportOptions(
    val format: ExportFormat,
    /** Requested scale of the canvas size; the tier limit may reduce it further. */
    val scale: Float = 1f,
    val quality: Int = 92,
    val fps: Int = 12,
    val includeAudio: Boolean = true,
    val dither: Boolean = false,
    val highBitrate: Boolean = false,
    /** Export only the given frame for still formats. */
    val frameIndex: Int = 0,
)

enum class ExportError { NOTHING_TO_EXPORT, ENCODER_UNAVAILABLE, STORAGE, FEATURE_LOCKED, UNKNOWN }

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val uri: Uri, val shareUri: Uri, val mime: String, val displayName: String, val sizeBytes: Long) : ExportState
    data class Failed(val error: ExportError) : ExportState
}

class ExportException(val error: ExportError, message: String? = null) : IOException(message)

/**
 * Renders and encodes exports in the background. Output is first written to a cache file,
 * then copied to the chosen destination and verified; success is only reported for files that
 * really exist with the expected size.
 */
class ExportManager(
    private val context: Context,
    private val cellLoader: (CellKey) -> Bitmap?,
    private val audioFile: (AudioClip) -> File,
) {
    private val compositor = Compositor()

    /** Size the export will actually have after applying the plan's limits. */
    fun outputSize(doc: ProjectDocument, options: ExportOptions, gate: FeatureGate): Pair<Int, Int> =
        gate.fitExportSize(doc.width, doc.height, options.scale)

    suspend fun export(
        doc: ProjectDocument,
        options: ExportOptions,
        gate: FeatureGate,
        destination: Uri?,
        progress: (Float) -> Unit,
    ): ExportState.Done = withContext(Dispatchers.Default) {
        val job = this.coroutineContext
        options.format.feature?.let { if (!gate.check(it).isGranted) throw ExportException(ExportError.FEATURE_LOCKED) }
        val fps = gate.clampFps(options.fps)
        var (w, h) = outputSize(doc, options, gate)
        val name = sanitize(doc.name) + "_" + System.currentTimeMillis() / 1000 + "." + options.format.extension
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val out = File(dir, name)
        val cells = cachedSource()

        when (options.format) {
            ExportFormat.PNG, ExportFormat.PNG_TRANSPARENT, ExportFormat.JPG -> {
                val transparent = options.format == ExportFormat.PNG_TRANSPARENT
                val bmp = render(doc, options.frameIndex.coerceIn(0, doc.frames.lastIndex), cells, w, h, transparent, opaque = options.format == ExportFormat.JPG)
                out.outputStream().use { stream ->
                    val format = if (options.format == ExportFormat.JPG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    if (!bmp.compress(format, options.quality.coerceIn(10, 100), stream)) throw ExportException(ExportError.UNKNOWN)
                }
                progress(1f)
            }
            ExportFormat.GIF -> {
                val transparent = doc.backgroundColor == 0
                val clock = PlaybackClock(doc.frames, fps)
                out.outputStream().buffered().use { stream ->
                    val encoder = GifEncoder(stream, w, h, loop = doc.loop, transparent = transparent)
                    val pixels = IntArray(w * h)
                    for (i in doc.frames.indices) {
                        job.ensureActive()
                        val bmp = render(doc, i, cells, w, h, transparent = transparent, opaque = !transparent)
                        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                        bmp.recycle()
                        encoder.addFrame(pixels, clock.frameDurationUs(i) / 1000.0, dither = options.dither && gate.check(Feature.GIF_DITHERING).isGranted)
                        progress((i + 1f) / doc.frames.size)
                    }
                    encoder.finish()
                }
            }
            ExportFormat.MP4 -> {
                val size = Mp4Encoder.supportedSize(w, h) ?: throw ExportException(ExportError.ENCODER_UNAVAILABLE)
                w = size.first
                h = size.second
                val clock = PlaybackClock(doc.frames, fps)
                val audio = if (options.includeAudio && doc.audioClips.any { !it.muted }) {
                    buildAudio(doc, clock.totalDurationMs)
                } else {
                    null
                }
                val bpp = if (options.highBitrate && gate.check(Feature.HIGH_BITRATE_VIDEO).isGranted) 0.28 else 0.12
                val bitrate = (w.toLong() * h * fps * bpp).toLong().coerceIn(1_000_000L, 80_000_000L).toInt()
                var cachedIndex = -1
                var cachedBitmap: Bitmap? = null
                Mp4Encoder(out, w, h, fps, bitrate).encode(clock.totalTicks, audio, { tick ->
                    job.ensureActive()
                    val index = clock.frameAtTick(tick)
                    if (index != cachedIndex) {
                        cachedBitmap?.recycle()
                        cachedBitmap = render(doc, index, cells, w, h, transparent = false, opaque = true)
                        cachedIndex = index
                    }
                    cachedBitmap!!
                }, progress)
                cachedBitmap?.recycle()
            }
            ExportFormat.PNG_SEQUENCE -> {
                ZipOutputStream(out.outputStream().buffered()).use { zip ->
                    for (i in doc.frames.indices) {
                        job.ensureActive()
                        val bmp = render(doc, i, cells, w, h, transparent = doc.backgroundColor == 0, opaque = false)
                        zip.putNextEntry(ZipEntry("%s_%05d.png".format(sanitize(doc.name), i + 1)))
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, zip)
                        zip.closeEntry()
                        bmp.recycle()
                        progress((i + 1f) / doc.frames.size)
                    }
                }
            }
        }

        if (!out.exists() || out.length() == 0L) throw ExportException(ExportError.UNKNOWN, "Output missing")
        val target = destination ?: insertIntoMediaStore(name, options.format)
        copyAndVerify(out, target)
        val share = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out)
        ExportState.Done(target, share, options.format.mime, name, out.length())
    }

    private fun cachedSource(): CellSource {
        val cache = object : LruCache<CellKey, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
            override fun sizeOf(key: CellKey, value: Bitmap) = value.allocationByteCount
        }
        val empty = HashSet<CellKey>()
        return CellSource { key ->
            if (key in empty) return@CellSource null
            cache.get(key) ?: cellLoader(key)?.also { cache.put(key, it) } ?: run { empty += key; null }
        }
    }

    private fun render(
        doc: ProjectDocument,
        frame: Int,
        cells: CellSource,
        w: Int,
        h: Int,
        transparent: Boolean,
        opaque: Boolean,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hasVisibleBackground = doc.layers.any { it.isBackground && it.visible } && doc.backgroundColor != 0
        if (opaque && !hasVisibleBackground) canvas.drawColor(0xFFFFFFFF.toInt())
        canvas.scale(w / doc.width.toFloat(), h / doc.height.toFloat())
        val hidden = if (transparent) doc.layers.filter { it.isBackground }.map { it.id }.toSet() else emptySet()
        compositor.drawFrame(canvas, doc, frame, cells, com.godraw.app.engine.RenderOptions(hiddenLayerIds = hidden))
        return bmp
    }

    private fun buildAudio(doc: ProjectDocument, durationMs: Long): EncodedTrack? {
        val clips = doc.audioClips.filter { !it.muted }.mapNotNull { clip ->
            val pcm = AudioCodecs.decode(audioFile(clip), clip.effectiveEndMs + 100) ?: return@mapNotNull null
            MixClip(pcm, clip.startMs, clip.trimStartMs, clip.effectiveEndMs, clip.volume)
        }
        if (clips.isEmpty()) return null
        val mixed = PcmMixer.mix(clips, durationMs)
        return AudioCodecs.encodeAac(mixed)
    }

    private fun insertIntoMediaStore(name: String, format: ExportFormat): Uri {
        val resolver = context.contentResolver
        val (collection, relative) = when (format) {
            ExportFormat.MP4 -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_MOVIES + "/GoDraw"
            ExportFormat.PNG_SEQUENCE -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_DOWNLOADS + "/GoDraw"
            else -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_PICTURES + "/GoDraw"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection, values) ?: throw ExportException(ExportError.STORAGE, "MediaStore insert failed")
    }

    private fun copyAndVerify(source: File, target: Uri) {
        val resolver = context.contentResolver
        try {
            val written = resolver.openOutputStream(target, "w")?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: throw ExportException(ExportError.STORAGE, "Cannot open destination")
            if (written != source.length()) throw ExportException(ExportError.STORAGE, "Incomplete copy")
            if (target.authority == MediaStore.AUTHORITY) {
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            val size = resolver.openFileDescriptor(target, "r")?.use { it.statSize } ?: -1L
            if (size in 0 until source.length()) throw ExportException(ExportError.STORAGE, "Verification failed")
        } catch (e: ExportException) {
            if (target.authority == MediaStore.AUTHORITY) runCatching { resolver.delete(target, null, null) }
            throw e
        } catch (e: Exception) {
            if (target.authority == MediaStore.AUTHORITY) runCatching { resolver.delete(target, null, null) }
            throw ExportException(ExportError.STORAGE, e.message)
        }
    }

    fun shareIntent(done: ExportState.Done): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = done.mime
            putExtra(Intent.EXTRA_STREAM, done.shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().replace(' ', '_').ifEmpty { "GoDraw" }.take(60)
}
