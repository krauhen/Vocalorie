package com.example.vocalorie.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Marker identifying a Vocalorie backup file, so unrelated JSON is rejected on import. */
const val BACKUP_FORMAT: String = "vocalorie-backup"

/**
 * Mirrors the Room `@Database(version = ...)` in [VocalorieDatabase]. Keep in sync on every schema
 * bump: a file whose [BackupEnvelope.schemaVersion] differs is refused rather than imported.
 */
const val BACKUP_SCHEMA_VERSION: Int = 8

/**
 * Full-database backup: all four user-data tables (never any secret such as an API key). Each row
 * keeps its primary key so import can match and skip rows that already exist.
 */
@Serializable
data class BackupEnvelope(
    val format: String = BACKUP_FORMAT,
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long = 0L,
    val meals: List<MealEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val cachedMeals: List<CachedMealEntity> = emptyList(),
    val cachedItems: List<CachedItemEntity> = emptyList(),
)

/** How many rows a merge-import inserted versus skipped (already present by primary key). */
data class BackupImportResult(val imported: Int, val skipped: Int)

/** Thrown when a file is not a recognizable backup or targets an unsupported schema version. */
class BackupFormatException(message: String) : Exception(message)

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** Encode a backup envelope to its JSON string form. */
fun encodeBackupEnvelope(envelope: BackupEnvelope): String =
    backupJson.encodeToString(BackupEnvelope.serializer(), envelope)

/**
 * Decode and validate a backup JSON string. Throws [BackupFormatException] when the text is not a
 * recognizable backup or targets a different schema version — pure, so callers can reject a file
 * before touching the database.
 */
fun parseBackupEnvelope(json: String): BackupEnvelope {
    val envelope = runCatching { backupJson.decodeFromString(BackupEnvelope.serializer(), json) }
        .getOrElse { throw BackupFormatException("This file is not a valid Vocalorie backup.") }
    if (envelope.format != BACKUP_FORMAT) {
        throw BackupFormatException("This file is not a Vocalorie backup.")
    }
    if (envelope.schemaVersion != BACKUP_SCHEMA_VERSION) {
        throw BackupFormatException(
            "Backup is schema v${envelope.schemaVersion}; this app expects v$BACKUP_SCHEMA_VERSION.",
        )
    }
    return envelope
}

/** The rows in [incoming] whose key is not already present in [existingKeys] (merge, skip-if-present). */
fun <T, K> selectNewBackupRows(incoming: List<T>, existingKeys: Set<K>, keyOf: (T) -> K): List<T> =
    incoming.filter { keyOf(it) !in existingKeys }

/** Encode the entire database to a backup JSON string. Call off the main thread. */
fun exportBackupJson(db: VocalorieDatabase, exportedAtEpochMillis: Long): String {
    val envelope = BackupEnvelope(
        exportedAtEpochMillis = exportedAtEpochMillis,
        meals = db.mealDao().getAll(),
        activities = db.activityDao().getAll(),
        cachedMeals = db.cacheDao().getAllMeals(),
        cachedItems = db.cacheDao().getAllItems(),
    )
    return encodeBackupEnvelope(envelope)
}

/**
 * Merge a backup file into the database: insert only rows whose primary key is not already present,
 * never overwriting existing rows. Matching keys are the `id` for meals/activities and the
 * `normalizedKey`/`normalizedName` for the caches. Runs in a single transaction. Call off the main
 * thread. Throws [BackupFormatException] before any write when the file is unrecognized or targets a
 * different schema version, so a rejected import applies nothing.
 */
fun importBackupJson(db: VocalorieDatabase, json: String): BackupImportResult {
    val envelope = parseBackupEnvelope(json)

    val mealDao = db.mealDao()
    val activityDao = db.activityDao()
    val cacheDao = db.cacheDao()

    var imported = 0
    var skipped = 0
    db.runInTransaction {
        val newMeals = selectNewBackupRows(envelope.meals, mealDao.getAll().mapTo(HashSet()) { it.id }) { it.id }
        newMeals.forEach { mealDao.insert(it) }

        val newActivities = selectNewBackupRows(envelope.activities, activityDao.getAll().mapTo(HashSet()) { it.id }) { it.id }
        newActivities.forEach { activityDao.insert(it) }

        val newCachedMeals = selectNewBackupRows(envelope.cachedMeals, cacheDao.getAllMeals().mapTo(HashSet()) { it.normalizedKey }) { it.normalizedKey }
        newCachedMeals.forEach { cacheDao.upsertMeal(it) }

        val newCachedItems = selectNewBackupRows(envelope.cachedItems, cacheDao.getAllItems().mapTo(HashSet()) { it.normalizedName }) { it.normalizedName }
        if (newCachedItems.isNotEmpty()) cacheDao.upsertItems(newCachedItems)

        imported = newMeals.size + newActivities.size + newCachedMeals.size + newCachedItems.size
        val total = envelope.meals.size + envelope.activities.size + envelope.cachedMeals.size + envelope.cachedItems.size
        skipped = total - imported
    }

    return BackupImportResult(imported, skipped)
}
