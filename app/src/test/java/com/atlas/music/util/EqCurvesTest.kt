package com.atlas.music.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EqCurvesTest {

    @Test
    fun `empty or all-zero bands resolve to Flat`() {
        assertEquals("Flat", EqCurves.matchingPresetName(emptyList()))
        assertEquals("Flat", EqCurves.matchingPresetName(List(9) { 0 }))
    }

    @Test
    fun `stored curve resolves back to exact preset name`() {
        EqCurves.CURVES.forEach { (name, gains) ->
            assertEquals(name, EqCurves.matchingPresetName(gains))
        }
    }

    @Test
    fun `custom band edits resolve to Custom`() {
        assertEquals("Custom", EqCurves.matchingPresetName(listOf(250, 0, 0, 0, 0, 0, 0, 0, 0)))
    }
}
