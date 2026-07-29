package com.example.vocalorie.ui.voice

import android.speech.SpeechRecognizer

object VoiceListeningSessionPolicy {
    fun shouldRestartAfterResults(keepListening: Boolean): Boolean = keepListening

    fun shouldRestartAfterError(error: Int, keepListening: Boolean): Boolean = keepListening && when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        -> true
        else -> false
    }

    /**
     * Whether a restart that was queued a moment ago may still start the recognizer.
     *
     * A restart is queued, then delayed so the recognizer can settle. This has to be asked again
     * *after* that delay: an explicit Stop — or the sheet being disabled by an estimate or save
     * starting — during the window must win, otherwise the microphone re-opens behind the user's back.
     */
    fun shouldStartQueuedRestart(keepListening: Boolean, enabled: Boolean): Boolean = keepListening && enabled

    /**
     * Whether going to the background must tear the session down.
     *
     * Anything that could still hold the microphone open counts: a live recognition, or continuous
     * listening that would re-open it on the next queued restart. The sheet is off-screen at that
     * point, so the user has no Stop button to reach for.
     */
    fun shouldReleaseOnBackground(isListening: Boolean, keepListening: Boolean): Boolean =
        isListening || keepListening
}
