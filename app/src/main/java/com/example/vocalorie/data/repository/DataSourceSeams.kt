package com.example.vocalorie.data.repository

import kotlinx.coroutines.flow.Flow

/** The Room table names the repositories observe, so no repository spells one out as a literal. */
object VocalorieTables {
    const val MEALS: String = "meals"
    const val ACTIVITIES: String = "activities"
    const val CACHED_MEALS: String = "cached_meals"
    const val CACHED_ITEMS: String = "cached_items"
}

/**
 * Change notification from the database itself: one emission per committed change touching any of
 * the named tables, starting with the current state.
 *
 * An interface rather than a direct `invalidationTracker` call because an abstract Room `@Database`
 * cannot be instantiated on the JVM, and the repositories are unit-tested there.
 */
fun interface TableChangeSource {
    fun changes(vararg tables: String): Flow<Unit>
}

/**
 * Runs a group of DAO calls as one atomic unit: either every write inside [inTransaction] is
 * committed or none is.
 *
 * Not a `fun interface`: the method is generic, which a SAM conversion cannot express.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
