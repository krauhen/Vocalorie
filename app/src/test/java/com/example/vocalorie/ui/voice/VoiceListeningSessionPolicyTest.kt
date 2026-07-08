package com.example.vocalorie.ui.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceListeningSessionPolicyTest {
    @Test
    fun restartsAfterNormalSpeechResultsWhenContinuousListeningIsActive() {
        assertTrue(VoiceListeningSessionPolicy.shouldRestartAfterResults(keepListening = true))
        assertFalse(VoiceListeningSessionPolicy.shouldRestartAfterResults(keepListening = false))
    }

    @Test
    fun restartsAfterSilenceTimeoutsButNotFatalErrorsWhenContinuousListeningIsActive() {
        assertTrue(
            VoiceListeningSessionPolicy.shouldRestartAfterError(
                error = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                keepListening = true,
            ),
        )
        assertTrue(
            VoiceListeningSessionPolicy.shouldRestartAfterError(
                error = SpeechRecognizer.ERROR_NO_MATCH,
                keepListening = true,
            ),
        )
        assertFalse(
            VoiceListeningSessionPolicy.shouldRestartAfterError(
                error = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                keepListening = true,
            ),
        )
        assertFalse(
            VoiceListeningSessionPolicy.shouldRestartAfterError(
                error = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                keepListening = false,
            ),
        )
    }
}
