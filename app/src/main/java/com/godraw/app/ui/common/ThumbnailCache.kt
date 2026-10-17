package com.godraw.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Memory cache for project thumbnails, sized to ~1/16 of the app heap. */
class ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    suspend fun load(file: File, key: String, maxSize: Int = 512): Bitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            bmp?.also { cache.put(key, it) }
        }
    }
}

@Composable
fun rememberThumbnail(cache: ThumbnailCache, file: File, key: String): ImageBitmap? {
    val state by produceState(initialValue = cache.get(key)?.asImageBitmap(), file, key) {
        value = cache.load(file, key)?.asImageBitmap()
    }
    return state
}
