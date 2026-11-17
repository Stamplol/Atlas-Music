package com.atlas.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SoundStudioAndWheelTest {

    @Test
    fun `headphone profile search matches brand and model`() {
        val sony = HeadphoneProfiles.search("xm5")
        assertTrue(sony.any { it.id == "sony_wh1000xm5" })
        assertTrue(sony.any { it.id == "sony_wf1000xm5" })
        assertNotNull(HeadphoneProfiles.byId("sennheiser_hd600"))
    }

    @Test
    fun `effective curve applies negative auto headroom equal to max boost peak`() {
        val curve = AudioDspMath.computeEffectiveCurve(
            profileId = "sennheiser_hd600",
            userBandsMb = emptyList(),
            preampDb = 0f,
            soundCheckWarmth = 0f,
            soundCheckPunch = 0f,
            autoHeadroom = true,
        )
        assertEquals(10, curve.totalBandsDb10.size)
        val peak = curve.totalBandsDb10.maxOrNull() ?: 0f
        assertEquals(peak, curve.autoHeadroomCutDb, 0.01f)
        assertEquals(-peak, curve.effectivePreampDb, 0.01f)
    }

    @Test
    fun `channel balance attenuates opposite channel without boosting over preamp`() {
        val (lCenter, rCenter) = AudioDspMath.channelGainsDb(-2.0f, 0f)
        assertEquals(-2.0f, lCenter, 0.001f)
        assertEquals(-2.0f, rCenter, 0.001f)

        val (lRight, rRight) = AudioDspMath.channelGainsDb(-2.0f, 1.0f)
        assertEquals(-2.0f, rRight, 0.001f)
        assertTrue(lRight < -15.0f)
    }

    @Test
    fun `click wheel shortest angle handles -pi to pi wrap cleanly`() {
        val prev = (PI - 0.1).toFloat()
        val next = (-PI + 0.1).toFloat()
        val delta = ClickWheelMath.shortestAngleDelta(prev, next)
        assertEquals(0.2f, delta, 0.001f)

        val revDelta = ClickWheelMath.shortestAngleDelta(next, prev)
        assertEquals(-0.2f, revDelta, 0.001f)
    }

    @Test
    fun `click wheel detent accumulator emits clicks and preserves remainder`() {
        val step = 0.5f
        val (clicks1, rem1) = ClickWheelMath.accumulateClicks(0f, 1.2f, step)
        assertEquals(2, clicks1)
        assertEquals(0.2f, rem1, 0.001f)

        val (clicks2, rem2) = ClickWheelMath.accumulateClicks(rem1, -0.8f, step)
        assertEquals(-1, clicks2)
        assertEquals(-0.1f, rem2, 0.001f)
    }

    @Test
    fun `click wheel zone classification separates center and cardinal ring quadrants`() {
        assertEquals(ClickWheelZone.CENTER_SELECT, ClickWheelMath.zoneAt(100f, 100f, 100f, 0.36f, 105f, 105f))
        assertEquals(ClickWheelZone.RING_TOP_MENU, ClickWheelMath.zoneAt(100f, 100f, 100f, 0.36f, 100f, 20f))
        assertEquals(ClickWheelZone.RING_RIGHT_NEXT, ClickWheelMath.zoneAt(100f, 100f, 100f, 0.36f, 180f, 100f))
        assertEquals(ClickWheelZone.RING_BOTTOM_PLAY, ClickWheelMath.zoneAt(100f, 100f, 100f, 0.36f, 100f, 180f))
        assertEquals(ClickWheelZone.RING_LEFT_PREV, ClickWheelMath.zoneAt(100f, 100f, 100f, 0.36f, 20f, 100f))
        assertEquals(ClickWheelZone.OUTSIDE, ClickWheelMath.zoneAt(100f, 100f, 100f, 0.36f, 250f, 100f))
    }
}
