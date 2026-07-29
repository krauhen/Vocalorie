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

    @Test
    fun anExplicitStopDuringTheRestartDelayWinsOverTheQueuedRestart() {
        // Queued while still listening…
        assertTrue(VoiceListeningSessionPolicy.shouldStartQueuedRestart(keepListening = true, enabled = true))
        // …but Stop cleared `keepListening` during the 300 ms window, so it must not re-open the mic.
        assertFalse(VoiceListeningSessionPolicy.shouldStartQueuedRestart(keepListening = false, enabled = true))
        // An estimate or save starting mid-window disables the sheet; that also retires the restart.
        assertFalse(VoiceListeningSessionPolicy.shouldStartQueuedRestart(keepListening = true, enabled = false))
        assertFalse(VoiceListeningSessionPolicy.shouldStartQueuedRestart(keepListening = false, enabled = false))
    }

    @Test
    fun backgroundingReleasesAnythingThatCouldStillHoldTheMicrophone() {
        // Actively recognizing.
        assertTrue(VoiceListeningSessionPolicy.shouldReleaseOnBackground(isListening = true, keepListening = true))
        // Between utterances: not listening right now, but a queued restart would re-open the mic.
        assertTrue(VoiceListeningSessionPolicy.shouldReleaseOnBackground(isListening = false, keepListening = true))
        // Mid-teardown: the recognizer is still open even though continuous listening is off.
        assertTrue(VoiceListeningSessionPolicy.shouldReleaseOnBackground(isListening = true, keepListening = false))
        // Idle sheet: nothing to release, so backgrounding must not post a "stopped" message.
        assertFalse(VoiceListeningSessionPolicy.shouldReleaseOnBackground(isListening = false, keepListening = false))
    }
}
