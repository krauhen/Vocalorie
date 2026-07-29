package com.example.vocalorie.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolSettingsLabelsTest {
    @Test
    fun maxAgentIterationsDefaultsAndRangeAreExplicit() {
        assertEquals(24, ToolSettings().maxAgentIterations)
        assertEquals(16, ToolSettings.MIN_MAX_AGENT_ITERATIONS)
        assertEquals(128, ToolSettings.MAX_MAX_AGENT_ITERATIONS)
        assertEquals(16..128, ToolSettings.maxAgentIterationsRange)
    }

    @Test
    fun researchToolCallDefaultsAndRangeAreExplicit() {
        assertEquals(8, ToolSettings().maxResearchToolCalls)
        assertEquals(0, ToolSettings.MIN_MAX_RESEARCH_TOOL_CALLS)
        assertEquals(32, ToolSettings.MAX_MAX_RESEARCH_TOOL_CALLS)
        assertEquals(0..32, ToolSettings.maxResearchToolCallsRange)
    }

    @Test
    fun openAiModelDefaultsToGpt54Mini() {
        assertEquals(OpenAiModelChoice.GPT54MINI, ToolSettings().openAiModelChoice)
        assertEquals("GPT-5.4 mini", ToolSettingsLabels.openAiModelLabel(OpenAiModelChoice.GPT54MINI))
    }

    @Test
    fun braveKeyLabelShowsOnlySuffix() {
        assertEquals("efgh", ToolSettingsLabels.last4("  brave-abcd-efgh  "))
        assertEquals("Saved Brave key ending in efgh", ToolSettingsLabels.braveKeyLabel("efgh"))
        assertNull(ToolSettingsLabels.braveKeyLabel("  "))
    }

    @Test
    fun modeLabelsDescribeRealOnlyTools() {
        assertEquals(
            "Brave Search: API key required",
            ToolSettingsLabels.braveModeLabel(hasKey = false),
        )
        assertEquals(
            "WebFetch: real HTTP fetch enabled",
            ToolSettingsLabels.webFetchModeLabel(),
        )
    }
}
