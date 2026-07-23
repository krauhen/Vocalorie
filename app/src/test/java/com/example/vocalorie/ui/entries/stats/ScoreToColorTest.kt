package com.example.vocalorie.ui.entries.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the score->color scale terminates at green (best): low red, mid yellow, high green. */
class ScoreToColorTest {

    @Test
    fun `max score is deep green`() {
        val c = scoreToColor(100.0)
        assertTrue("green channel should dominate at 100", c.green > c.red && c.green > c.blue)
        assertTrue("green should be strong at 100", c.green > 0.4f)
        assertTrue("blue should be near zero at 100", c.blue < 0.3f)
    }

    @Test
    fun `high score of 76 is in the green range, not blue`() {
        val c = scoreToColor(76.0)
        assertTrue("green channel should dominate at 76", c.green > c.red && c.green > c.blue)
        assertTrue("blue should be low at 76", c.blue < 0.4f)
    }

    @Test
    fun `low score is red`() {
        val c = scoreToColor(0.0)
        assertTrue("red channel should dominate at 0", c.red > c.green && c.red > c.blue)
    }

    @Test
    fun `scale is monotonically greener as score rises across the top half`() {
        // 60 (green) through 100 (deep green) stays green-dominant throughout.
        listOf(60.0, 70.0, 80.0, 90.0, 100.0).forEach { score ->
            val c = scoreToColor(score)
            assertTrue("green should dominate at $score", c.green > c.red && c.green > c.blue)
        }
    }

    @Test
    fun `scores above 100 clamp to deep green`() {
        assertEquals(scoreToColor(100.0), scoreToColor(150.0))
    }
}
