package com.example.vocalorie.tools

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.asSequence
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsRealOnlyContractTest {
    @Test
    fun braveAndWebFetchSourcesDoNotContainMockToolGates() {
        val sourceRoot = listOf(
            Path.of("app", "src", "main", "java", "com", "example", "vocalorie"),
            Path.of("src", "main", "java", "com", "example", "vocalorie"),
        ).first(Files::exists)
        val forbidden = listOf(
            "useReal" + "BraveSearch",
            "useReal" + "WebFetch",
            "mock" + "Brave",
            "mock" + "WebFetch",
            "mock" + " fallback",
            "deterministic " + "mock",
            "Use real " + "Brave Search",
            "Use real " + "WebFetch",
        )

        val offenders = Files.walk(sourceRoot).use { paths ->
            paths.asSequence()
                .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
                .flatMap { path ->
                    val text = Files.readString(path)
                    forbidden.filter { text.contains(it, ignoreCase = true) }.map { "${path}: $it" }
                }
                .toList()
        }

        assertTrue("Forbidden mock/toggle source remains: $offenders", offenders.isEmpty())
    }
}
