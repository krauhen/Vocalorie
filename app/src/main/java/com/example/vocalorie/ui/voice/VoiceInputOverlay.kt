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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    attachedImages: List<GalleryImageAttachment>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SavedMeal>,
    onSearchMealClick: (SavedMeal) -> Unit,
    resetSignal: Int,
    onEstimate: () -> Unit,
    onReset: () -> Unit,
    onImagesChange: (List<GalleryImageAttachment>) -> Unit,
    onSave: (EditableMealDraft) -> Unit,
    /** Set when a grounding pass ran and failed, so the estimate is unsourced by accident. */
    groundingWarning: String? = null,
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
            "Add",
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
                attachedImages = attachedImages,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchResults = searchResults,
                onSearchMealClick = onSearchMealClick,
                resetSignal = resetSignal,
                onEstimate = onEstimate,
                onReset = onReset,
                onImagesChange = onImagesChange,
                onSave = onSave,
                groundingWarning = groundingWarning,
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
    attachedImages: List<GalleryImageAttachment>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SavedMeal>,
    onSearchMealClick: (SavedMeal) -> Unit,
    resetSignal: Int,
    onEstimate: () -> Unit,
    onReset: () -> Unit,
    onImagesChange: (List<GalleryImageAttachment>) -> Unit,
    onSave: (EditableMealDraft) -> Unit,
    enabled: Boolean,
    groundingWarning: String? = null,
) {
    val canReset = query.isNotBlank() || draft != null || attachedImages.isNotEmpty()
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
        SpeechInputControls(
            query = query,
            enabled = enabled,
            onTranscript = onQueryChange,
            attachedImages = attachedImages,
            onImagesChange = onImagesChange,
            resetSignal = resetSignal,
        )
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
        if (attachedImages.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attachedImages.forEach { attachment ->
                    attachment.previewImage?.let { preview ->
                        AttachedImageThumbnail(
                            label = attachment.label,
                            image = preview,
                            enabled = enabled,
                            onRemove = { onImagesChange(attachedImages - attachment) },
                        )
                    }
                }
            }
        }
        if (isLoading) LoadingRow("Estimating…")
        if (isSaving) LoadingRow("Saving locally…")
        saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        groundingWarning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
private fun AttachedImageThumbnail(
    label: String,
    image: ImageBitmap,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(72.dp)) {
        Image(
            bitmap = image,
            contentDescription = label,
            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
        Surface(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SpeechInputControls(
    query: String,
    enabled: Boolean,
    onTranscript: (String) -> Unit,
    attachedImages: List<GalleryImageAttachment>,
    onImagesChange: (List<GalleryImageAttachment>) -> Unit,
    resetSignal: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var keepListening by remember { mutableStateOf(false) }
    var isPreparingImages by remember { mutableStateOf(false) }
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
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_ATTACHMENTS),
    ) { uris ->
        if (uris.isEmpty()) {
            voiceMessage = "No photo selected."
        } else {
            val existingImages = attachedImages
            isPreparingImages = true
            voiceMessage = null
            scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) {
                        uris.map { uri -> runCatching { context.toGalleryImageAttachment(uri) } }
                    }
                    val newAttachments = prepared.mapNotNull { it.getOrNull() }
                    val failedCount = prepared.size - newAttachments.size
                    if (newAttachments.isEmpty()) {
                        voiceMessage = imagePreparationFailureMessage(failedCount)
                        return@launch
                    }
                    val merged = (existingImages + newAttachments).take(MAX_IMAGE_ATTACHMENTS)
                    onImagesChange(merged)
                    val droppedCount = existingImages.size + newAttachments.size - merged.size
                    val attachedMessage = if (droppedCount > 0) {
                        "Attached ${merged.size - existingImages.size} photo(s); $droppedCount dropped, max $MAX_IMAGE_ATTACHMENTS photos."
                    } else {
                        "Photo(s) attached: ${newAttachments.joinToString(", ") { it.label }}"
                    }
                    voiceMessage = if (failedCount > 0) {
                        "$attachedMessage ${imagePreparationFailureMessage(failedCount)}"
                    } else {
                        attachedMessage
                    }
                } finally {
                    isPreparingImages = false
                }
            }
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
        if (!enabled || isPreparingImages) return
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
            OutlinedButton(
                onClick = { pickImage() },
                enabled = enabled && !isPreparingImages,
                modifier = Modifier.weight(1f),
            ) {
                Text("Photo")
            }
        }
        if (isPreparingImages) LoadingRow("Preparing photos…")
        voiceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun imagePreparationFailureMessage(failedCount: Int): String =
    "$failedCount photo(s) could not be read."

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
