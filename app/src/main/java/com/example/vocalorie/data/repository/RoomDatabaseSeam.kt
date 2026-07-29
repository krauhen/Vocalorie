package com.example.vocalorie.data.repository

import androidx.room.withTransaction
import com.example.vocalorie.data.VocalorieDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Binds the repository seams to the real database: Room's own invalidation tracker is the single
 * source of change notification, and Room's suspending transaction is the unit of atomicity.
 *
 * The DAOs stay Java and blocking (KSP refuses to run alongside AGP's built-in Kotlin, so they
 * cannot return `Flow` or be `suspend`). That costs nothing here, because the repository — not the
 * DAO — is the seam callers depend on.
 */
class RoomDatabaseSeam(private val database: VocalorieDatabase) : TableChangeSource, TransactionRunner {

    override fun changes(vararg tables: String): Flow<Unit> = database.invalidationTracker
        .createFlow(tables = tables, emitInitialState = true)
        .map { }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)
}
