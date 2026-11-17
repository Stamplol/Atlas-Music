package com.atlas.music.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

enum class ClickWheelZone {
    OUTSIDE,
    CENTER_SELECT,
    RING_TOP_MENU,
    RING_RIGHT_NEXT,
    RING_BOTTOM_PLAY,
    RING_LEFT_PREV,
}

/**
 * Pure polar-coordinate geometry and rotational click accumulator for the iPod Click Wheel.
 * Handles angle wrap across the -pi/+pi discontinuity smoothly and triggers detents every
 * [stepRad] radians rotated along the ring.
 */
object ClickWheelMath {

    /** Default step per wheel click (~20 ticks per full revolution). */
    const val DEFAULT_STEP_RAD: Float = (2.0 * PI / 20.0).toFloat()

    /** Signed shortest-path angular delta in [-PI, PI]. */
    fun shortestAngleDelta(prevRad: Float, nextRad: Float): Float {
        var delta = nextRad - prevRad
        while (delta > PI) delta -= (2.0 * PI).toFloat()
        while (delta < -PI) delta += (2.0 * PI).toFloat()
        return delta
    }

    /** Polar angle around ([cx], [cy]) for pointer ([x], [y]), in [-PI, PI]. */
    fun angleAt(cx: Float, cy: Float, x: Float, y: Float): Float =
        atan2((y - cy).toDouble(), (x - cx).toDouble()).toFloat()

    /** Distance from center normalized by outer radius; >1 is outside wheel, <innerRatio is center disc. */
    fun zoneAt(
        cx: Float,
        cy: Float,
        outerRadius: Float,
        innerRatio: Float,
        x: Float,
        y: Float,
    ): ClickWheelZone {
        if (outerRadius <= 0f) return ClickWheelZone.OUTSIDE
        val dx = x - cx
        val dy = y - cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist > outerRadius) return ClickWheelZone.OUTSIDE
        if (dist <= outerRadius * innerRatio) return ClickWheelZone.CENTER_SELECT
        // Ring cardinal quadrants (atan2: 0 = right, +pi/2 = down, +/-pi = left, -pi/2 = top)
        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        return when {
            deg in -135.0..-45.0 -> ClickWheelZone.RING_TOP_MENU
            deg in -45.0..45.0 -> ClickWheelZone.RING_RIGHT_NEXT
            deg in 45.0..135.0 -> ClickWheelZone.RING_BOTTOM_PLAY
            else -> ClickWheelZone.RING_LEFT_PREV
        }
    }

    /**
     * Accumulate rotational movement and emit whole clicks (+ clockwise, - counter-clockwise)
     * plus remaining fractional radian accumulator.
     */
    fun accumulateClicks(
        pendingRad: Float,
        deltaRad: Float,
        stepRad: Float = DEFAULT_STEP_RAD,
    ): Pair<Int, Float> {
        if (stepRad <= 0f) return 0 to 0f
        var acc = pendingRad + deltaRad
        var clicks = 0
        while (acc >= stepRad) {
            acc -= stepRad
            clicks += 1
        }
        while (acc <= -stepRad) {
            acc += stepRad
            clicks -= 1
        }
        return clicks to acc
    }
}
