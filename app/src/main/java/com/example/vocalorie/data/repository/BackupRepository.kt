package com.example.vocalorie.data.repository

import com.example.vocalorie.data.BackupImportResult
import com.example.vocalorie.data.VocalorieDatabase
import com.example.vocalorie.data.exportBackupJson
import com.example.vocalorie.data.importBackupJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the text of a document the user chose, addressed by the string form of its URI.
 *
 * A seam rather than a direct `ContentResolver` call, so the backup path can be exercised without
 * the Android framework.
 */
interface DocumentTextStore {
    suspend fun read(documentUri: String): String

    suspend fun write(documentUri: String, text: String)
}

/**
 * Whole-database export and import against a user-chosen document.
 *
 * An interface because an abstract Room `@Database` cannot be instantiated on the JVM, so the state
 * holder's tests need a double for exactly this one repository.
 */
interface BackupRepository {
    suspend fun exportTo(documentUri: String, exportedAtEpochMillis: Long)

    suspend fun importFrom(documentUri: String): BackupImportResult
}

/**
 * The real backup repository. Encoding and decoding the whole database is transactional and
 * blocking, so it owns its own dispatching like every other repository here.
 */
class RoomBackupRepository(
    private val database: VocalorieDatabase,
    private val documents: DocumentTextStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupRepository {

    override suspend fun exportTo(documentUri: String, exportedAtEpochMillis: Long) {
        val json = withContext(dispatcher) { exportBackupJson(database, exportedAtEpochMillis) }
        documents.write(documentUri, json)
    }

    override suspend fun importFrom(documentUri: String): BackupImportResult {
        val json = documents.read(documentUri)
        return withContext(dispatcher) { importBackupJson(database, json) }
    }
}
