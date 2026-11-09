package io.github.ctjerm.lowlight.dimmer

import org.junit.Assert.assertEquals
import org.junit.Test

class DimmerLevelTest {
    @Test
    fun from_belowMinimum_clampsToFivePercent() {
        assertEquals(5, DimmerLevel.from(-20).percent)
    }

    @Test
    fun from_aboveMaximum_clampsToSeventyFivePercent() {
        assertEquals(75, DimmerLevel.from(100).percent)
    }

    @Test
    fun from_betweenSteps_roundsToNearestFivePercent() {
        assertEquals(40, DimmerLevel.from(42).percent)
        assertEquals(45, DimmerLevel.from(43).percent)
    }

    @Test
    fun opacity_convertsPercentToWindowAlpha() {
        assertEquals(0.45f, DimmerLevel.from(45).opacity, 0.0001f)
    }
}
