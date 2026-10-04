package io.github.manxisuo.spriglauncher.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackgroundImageLoader(private val context: Context) {
    suspend fun load(uriText: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriText))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = maxOf(width, height)
                if (largest > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / largest
                    decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
                }
            }
        }.getOrNull()
    }

    companion object { private const val MAX_EDGE = 2048 }
}
