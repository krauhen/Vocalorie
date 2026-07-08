package com.example.vocalorie.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiApiKeyLabelsTest {
    @Test
    fun last4TrimsKeyBeforeExtractingSuffix() {
        assertEquals("wxyz", OpenAiApiKeyLabels.last4("  test-key-abcd-wxyz  "))
    }

    @Test
    fun maskedLabelShowsOnlySuffix() {
        assertEquals("Saved key ending in 1234", OpenAiApiKeyLabels.maskedLabel("1234"))
    }

    @Test
    fun maskedLabelIsNullWithoutSuffix() {
        assertNull(OpenAiApiKeyLabels.maskedLabel("   "))
        assertNull(OpenAiApiKeyLabels.maskedLabel(null))
    }
}
