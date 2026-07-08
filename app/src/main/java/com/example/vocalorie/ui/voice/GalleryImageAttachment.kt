package com.example.vocalorie.ui.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

data class GalleryImageAttachment(
    val label: String,
    val image: AttachmentSource.Image,
    val previewImage: ImageBitmap? = null,
)

fun Context.toGalleryImageAttachment(uri: Uri): GalleryImageAttachment {
    val resolver = contentResolver
    val displayName = uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "selected image" }
    val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("Could not read the selected image.")
    val normalized = normalizeImageBytes(rawBytes)

    return GalleryImageAttachment(
        label = displayName,
        image = AttachmentSource.Image(
            content = AttachmentContent.Binary.Bytes(normalized.bytes),
            format = normalized.format,
            mimeType = normalized.mimeType,
            fileName = displayName,
        ),
        previewImage = normalized.previewBitmap.asImageBitmap(),
    )
}

private data class NormalizedImageBytes(
    val bytes: ByteArray,
    val format: String,
    val mimeType: String,
    val previewBitmap: Bitmap,
)

private fun normalizeImageBytes(rawBytes: ByteArray): NormalizedImageBytes {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
    val originalWidth = bounds.outWidth.takeIf { it > 0 } ?: throw IllegalArgumentException("Unsupported image format.")
    val originalHeight = bounds.outHeight.takeIf { it > 0 } ?: throw IllegalArgumentException("Unsupported image format.")
    val sampleSize = calculateSampleSize(originalWidth, originalHeight, MAX_IMAGE_DIMENSION)

    val decoded = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = sampleSize
    }
    val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decoded)
        ?: throw IllegalArgumentException("Could not decode the selected image.")

    val scaledBitmap = bitmap.scaleDownIfNeeded(MAX_IMAGE_DIMENSION)
    val format = if (scaledBitmap.hasAlpha()) "png" else "jpg"
    val compressed = ByteArrayOutputStream().use { output ->
        val compressionFormat = if (format == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (format == "png") 100 else JPEG_QUALITY
        check(scaledBitmap.compress(compressionFormat, quality, output)) { "Could not compress the selected image." }
        output.toByteArray()
    }
    val previewBitmap = scaledBitmap.scaleDownIfNeeded(PREVIEW_MAX_DIMENSION)

    if (bitmap !== scaledBitmap && bitmap !== previewBitmap) bitmap.recycle()
    if (scaledBitmap !== previewBitmap) scaledBitmap.recycle()

    return NormalizedImageBytes(bytes = compressed, format = format, mimeType = format.toMimeType(), previewBitmap = previewBitmap)
}

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (halfWidth / sampleSize >= maxDimension || halfHeight / sampleSize >= maxDimension) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun Bitmap.scaleDownIfNeeded(maxDimension: Int): Bitmap {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxDimension) return this

    val scale = maxDimension.toFloat() / largestSide.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun String.toMimeType(): String = when (this) {
    "png" -> "image/png"
    "jpg" -> "image/jpeg"
    else -> error("Unsupported image format.")
}

const val MAX_IMAGE_ATTACHMENTS = 4

private const val MAX_IMAGE_DIMENSION = 1536
private const val PREVIEW_MAX_DIMENSION = 256
private const val JPEG_QUALITY = 90
