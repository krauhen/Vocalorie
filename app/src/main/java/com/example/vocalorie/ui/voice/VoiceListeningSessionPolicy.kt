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
}
