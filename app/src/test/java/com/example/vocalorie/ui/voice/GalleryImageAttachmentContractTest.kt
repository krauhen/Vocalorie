package com.example.vocalorie.ui.voice

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryImageAttachmentContractTest {
    @Test
    fun galleryImageAttachmentSetsAConcreteMimeTypeForBinaryImageUploads() {
        val source = source("app/src/main/java/com/example/vocalorie/ui/voice/GalleryImageAttachment.kt")

        assertTrue(source.contains("AttachmentSource.Image("))
        assertTrue(source.contains("content = AttachmentContent.Binary.Bytes(normalized.bytes)"))
        assertTrue(source.contains("format = normalized.format"))
        assertTrue(source.contains("mimeType = normalized.mimeType"))
    }

    private fun source(path: String): String = File(path).takeIf { it.exists() }
        ?.readText()
        ?: File("../$path").readText()
}
