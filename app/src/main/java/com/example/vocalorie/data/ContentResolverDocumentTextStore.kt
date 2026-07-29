package com.example.vocalorie.data

import android.content.Context
import android.net.Uri
import com.example.vocalorie.data.repository.DocumentTextStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real [DocumentTextStore]: reads and writes a document the user picked through the system
 * document picker.
 *
 * A data source, so it is the one layer here that takes a `Context`. The URI is carried as its
 * string form, which keeps `android.net.Uri` — and therefore the whole Android framework — out of
 * the repository and the state holder above it.
 */
class ContentResolverDocumentTextStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentTextStore {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(documentUri: String): String = withContext(dispatcher) {
        resolver.openInputStream(Uri.parse(documentUri))?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the chosen file for reading.")
    }

    override suspend fun write(documentUri: String, text: String) {
        withContext(dispatcher) {
            resolver.openOutputStream(Uri.parse(documentUri))?.use { it.write(text.toByteArray()) }
                ?: error("Could not open the chosen file for writing.")
        }
    }
}
