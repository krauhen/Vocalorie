package com.example.vocalorie.testsupport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProductionSourceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolvesAnExistingProductionSourceFileByName() {
        assertTrue(productionSource("MealEditor.kt").contains("fun ReadOnlyMealSummary("))
    }

    @Test
    fun failsLoudlyInsteadOfReturningEmptyTextWhenNoFileMatches() {
        val failure = assertThrows(IllegalStateException::class.java) {
            productionSource("ThisFileDoesNotExist.kt")
        }

        assertTrue(failure.message!!.contains("found 0"))
    }

    @Test
    fun failsLoudlyWhenMoreThanOneFileMatches() {
        val root = tempFolder.newFolder("root")
        root.resolve("a").apply { mkdirs() }.resolve("Duplicate.kt").writeText("one")
        root.resolve("b").apply { mkdirs() }.resolve("Duplicate.kt").writeText("two")

        val failure = assertThrows(IllegalStateException::class.java) {
            resolveSingleFile("Duplicate.kt", root)
        }

        assertTrue(failure.message!!.contains("found 2"))
    }

    @Test
    fun resolvesAUniqueMatchNestedAtAnyDepth() {
        val root = tempFolder.newFolder("root")
        val nested = root.resolve("deeply/nested/package").apply { mkdirs() }
        nested.resolve("Unique.kt").writeText("payload")

        assertEquals("payload", resolveSingleFile("Unique.kt", root).readText())
    }
}
