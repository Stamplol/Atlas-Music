// Atlas iOS — Polar geometry and rotational click accumulator for the iPod click wheel.
import Foundation

/// Mirrors Android `ClickWheelMath`. `ClickWheelZone` itself lives in Core/Models/Enums.swift.
enum ClickWheelMath {
    /// Default step per wheel click (~20 ticks per full revolution).
    static let defaultStepRad: Float = Float(2.0 * Double.pi / 20.0)

    /// Signed shortest-path angular delta in [-PI, PI].
    static func shortestAngleDelta(prevRad: Float, nextRad: Float) -> Float {
        let twoPi = Float(2.0 * Double.pi)
        var delta = nextRad - prevRad
        while delta > Float(Double.pi) { delta -= twoPi }
        while delta < -Float(Double.pi) { delta += twoPi }
        return delta
    }

    /// Polar angle around (cx, cy) for pointer (x, y), in [-PI, PI].
    static func angleAt(cx: Float, cy: Float, x: Float, y: Float) -> Float {
        Float(atan2(Double(y - cy), Double(x - cx)))
    }

    /// Distance-based zone lookup with cardinal ring quadrants.
    static func zoneAt(cx: Float, cy: Float, outerRadius: Float, innerRatio: Float,
                       x: Float, y: Float) -> ClickWheelZone {
        if outerRadius <= 0 { return .outside }
        let dx = x - cx
        let dy = y - cy
        let dist = Float(hypot(Double(dx), Double(dy)))
        if dist > outerRadius { return .outside }
        if dist <= outerRadius * innerRatio { return .centerSelect }

        // atan2: 0 = right, +pi/2 = down, +/-pi = left, -pi/2 = top.
        let deg = Double(atan2(Double(dy), Double(dx))) * 180.0 / Double.pi
        if (-135.0...(-45.0)).contains(deg) { return .ringTopMenu }
        if (-45.0...45.0).contains(deg) { return .ringRightNext }
        if (45.0...135.0).contains(deg) { return .ringBottomPlay }
        return .ringLeftPrev
    }

    /// Accumulate rotation and emit whole clicks (+ clockwise, - counter-clockwise)
    /// plus the remaining fractional radian accumulator.
    static func accumulateClicks(pendingRad: Float, deltaRad: Float,
                                 stepRad: Float = defaultStepRad) -> (Int, Float) {
        if stepRad <= 0 { return (0, 0) }
        var acc = pendingRad + deltaRad
        var clicks = 0
        while acc >= stepRad {
            acc -= stepRad
            clicks += 1
        }
        while acc <= -stepRad {
            acc += stepRad
            clicks -= 1
        }
        return (clicks, acc)
    }
}
