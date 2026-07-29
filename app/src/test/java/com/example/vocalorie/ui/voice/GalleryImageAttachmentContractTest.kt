package com.example.vocalorie.ui.voice

import com.example.vocalorie.testsupport.productionSource
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryImageAttachmentContractTest {
    @Test
    fun galleryImageAttachmentSetsAConcreteMimeTypeForBinaryImageUploads() {
        val source = productionSource("GalleryImageAttachment.kt")

        assertTrue(source.contains("AttachmentSource.Image("))
        assertTrue(source.contains("content = AttachmentContent.Binary.Bytes(normalized.bytes)"))
        assertTrue(source.contains("format = normalized.format"))
        assertTrue(source.contains("mimeType = normalized.mimeType"))
    }

}
