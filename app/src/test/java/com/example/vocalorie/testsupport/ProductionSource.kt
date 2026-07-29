package com.example.vocalorie.testsupport

import java.io.File

/**
 * Resolves a production source file by its filename rather than by a hardcoded path, so the
 * source-text contract tests survive file moves and package renames.
 *
 * Deliberately fails loudly on 0 or more than 1 match: a path that no longer resolves used to
 * yield empty text, which made every `assertFalse(source.contains(...))` pass vacuously.
 */
fun productionSource(fileName: String): String = productionSourceFile(fileName).readText()

fun productionSourceFile(fileName: String): File = resolveSingleFile(fileName, productionSourceRoot())

internal fun resolveSingleFile(fileName: String, root: File): File {
    val matches = root.walkTopDown().filter { it.isFile && it.name == fileName }.toList()
    check(matches.size == 1) {
        "Expected exactly one production source file named '$fileName' under $root, " +
            "found ${matches.size}${matches.joinToString(prefix = ": ") { it.path }}"
    }
    return matches.single()
}

fun productionSourceRoot(): File = listOf(
    File("app/src/main/java"),
    File("src/main/java"),
    File("../app/src/main/java"),
).firstOrNull { it.isDirectory }
    ?: error(
        "Could not locate app/src/main/java from working directory ${File("").absolutePath}",
    )
