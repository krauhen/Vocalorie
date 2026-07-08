package com.example.vocalorie.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.ui.components.EditableMealEditor
import com.example.vocalorie.ui.components.ErrorCard
import com.example.vocalorie.ui.components.LoadingRow
import com.example.vocalorie.ui.voice.toGalleryImageAttachment
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    draft: EditableMealDraft?,
    onDraftChange: (EditableMealDraft) -> Unit,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    diagnostic: String?,
    saveMessage: String?,
    attachedImageLabel: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SavedMeal>,
    onSearchMealClick: (SavedMeal) -> Unit,
    resetSignal: Int,
    onEstimate: () -> Unit,
    onReset: () -> Unit,
    onPickImage: (GalleryImageAttachment?) -> Unit,
    onSave: (EditableMealDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        onClick = { showSheet = true },
        modifier = modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            "Add meal",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            VoiceSheetContent(
                query = query,
                onQueryChange = onQueryChange,
                draft = draft,
                onDraftChange = onDraftChange,
                isLoading = isLoading,
                isSaving = isSaving,
                error = error,
                diagnostic = diagnostic,
                saveMessage = saveMessage,
                attachedImageLabel = attachedImageLabel,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchResults = searchResults,
                onSearchMealClick = onSearchMealClick,
                resetSignal = resetSignal,
                onEstimate = onEstimate,
                onReset = onReset,
                onPickImage = onPickImage,
                onSave = onSave,
                enabled = !isLoading && !isSaving,
            )
        }
    }
}

@Composable
private fun VoiceSheetContent(
    query: String,
    onQueryChange: (String) -> Unit,
    draft: EditableMealDraft?,
    onDraftChange: (EditableMealDraft) -> Unit,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    diagnostic: String?,
    saveMessage: String?,
    attachedImageLabel: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SavedMeal>,
    onSearchMealClick: (SavedMeal) -> Unit,
    resetSignal: Int,
    onEstimate: () -> Unit,
    onReset: () -> Unit,
    onPickImage: (GalleryImageAttachment?) -> Unit,
    onSave: (EditableMealDraft) -> Unit,
    enabled: Boolean,
) {
    val canReset = query.isNotBlank() || draft != null || attachedImageLabel != null
    val canSave = draft != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("New meal estimate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Speak or type a meal, then review the estimate before it becomes a saved entry.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Meal description") },
            minLines = 3,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search saved meals") },
            singleLine = true,
            enabled = enabled,
        )
        if (searchQuery.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                searchResults.take(5).forEach { meal ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onSearchMealClick(meal) },
                        colors = CardDefaults.cardColors(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(meal.title.ifBlank { meal.query }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                meal.query,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (searchResults.isEmpty()) {
                    Text("No saved meals matched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SpeechInputControls(query = query, enabled = enabled, onTranscript = onQueryChange, onPickImage = onPickImage, resetSignal = resetSignal)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEstimate, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(if (error == null) "Estimate" else "Retry")
            }
            OutlinedButton(onClick = onReset, enabled = enabled && canReset, modifier = Modifier.weight(1f)) {
                Text("Reset")
            }
            Button(onClick = { draft?.let(onSave) }, enabled = enabled && canSave, modifier = Modifier.weight(1f)) {
                Text("Save entry")
            }
        }
        attachedImageLabel?.let {
            Text("Photo: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (isLoading) LoadingRow("Estimating…")
        if (isSaving) LoadingRow("Saving locally…")
        saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        error?.let { ErrorCard(message = it, diagnostic = diagnostic, onRetry = onEstimate, enabled = enabled) }
        draft?.let {
            EditableMealEditor(
                draft = it,
                onDraftChange = onDraftChange,
                enabled = enabled,
                actionLabel = null,
                onAction = null,
            )
        }
    }
}

@Composable
private fun SpeechInputControls(
    query: String,
    enabled: Boolean,
    onTranscript: (String) -> Unit,
    onPickImage: (GalleryImageAttachment?) -> Unit,
    resetSignal: Int,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var keepListening by remember { mutableStateOf(false) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var restartRequest by remember { mutableStateOf(0) }
    var stoppingManually by remember { mutableStateOf(false) }
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startAfterPermission = true
        } else {
            keepListening = false
            voiceMessage = "Microphone permission denied."
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            voiceMessage = "No photo selected."
        } else {
            val attachment = context.toGalleryImageAttachment(uri)
            onPickImage(attachment)
            voiceMessage = "Photo attached: ${attachment.label}"
        }
    }

    DisposableEffect(speechRecognizer) { onDispose { speechRecognizer?.destroy() } }

    LaunchedEffect(resetSignal) {
        voiceMessage = null
    }

    fun appendTranscript(transcript: String) {
        val nextQuery = listOf(query.trim(), transcript.trim())
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
        onTranscript(nextQuery)
    }

    fun requestRestart() {
        restartRequest += 1
    }

    fun startListening() {
        if (!enabled || isListening) return
        val recognizer = speechRecognizer
        when {
            recognizer == null -> {
                keepListening = false
                voiceMessage = "No Android speech recognition service is available on this device."
            }
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> {
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { voiceMessage = "Listening… tap again to stop." }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        isListening = false
                        if (keepListening) voiceMessage = "Processing speech…"
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        val wasStoppingManually = stoppingManually
                        if (wasStoppingManually || !keepListening) {
                            stoppingManually = false
                            voiceMessage = if (wasStoppingManually) "Voice input stopped." else speechErrorMessage(error)
                            return
                        }
                        if (VoiceListeningSessionPolicy.shouldRestartAfterError(error, keepListening)) {
                            voiceMessage = "Still listening…"
                            requestRestart()
                        } else {
                            keepListening = false
                            voiceMessage = speechErrorMessage(error)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (transcript.isBlank()) {
                            voiceMessage = if (keepListening) "Still listening…" else "No speech transcript returned."
                        } else {
                            appendTranscript(transcript)
                            voiceMessage = if (keepListening) "Voice transcript added. Still listening…" else "Voice transcript added."
                        }
                        if (VoiceListeningSessionPolicy.shouldRestartAfterResults(keepListening)) {
                            requestRestart()
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your meal")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                }
                isListening = true
                recognizer.startListening(intent)
            }
        }
    }

    fun stopListening() {
        keepListening = false
        stoppingManually = true
        isListening = false
        voiceMessage = "Voice input stopped."
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
    }

    fun pickImage() {
        if (!enabled) return
        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(startAfterPermission) {
        if (startAfterPermission) {
            startAfterPermission = false
            keepListening = true
            startListening()
        }
    }

    LaunchedEffect(restartRequest) {
        if (restartRequest > 0 && keepListening && enabled) {
            delay(300)
            startListening()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (keepListening) {
                        stopListening()
                    } else {
                        keepListening = true
                        startListening()
                    }
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (keepListening) "Stop" else "Voice")
            }
            OutlinedButton(onClick = { pickImage() }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Photo")
            }
        }
        voiceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun speechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Speech input audio error."
    SpeechRecognizer.ERROR_CLIENT -> "Speech input client error."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for speech input."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error."
    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found. Try again."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
    SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
    else -> "Speech recognition failed."
}
