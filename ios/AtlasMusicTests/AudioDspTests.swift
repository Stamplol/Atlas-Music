import XCTest
@testable import AtlasMusic

// Atlas iOS — XCTest port of the Android SoundStudioAndWheelTest (headphone profiles, DSP math, click wheel geometry).

// MARK: - HeadphoneProfiles

final class HeadphoneProfileTests: XCTestCase {
    func testHeadphoneProfileSearchMatchesBrandAndModel() {
        let search = HeadphoneProfiles.search
        let byID = HeadphoneProfiles.byID
        let sony = search("xm5")
        XCTAssertTrue(sony.contains { $0.id == "sony_wh1000xm5" })
        XCTAssertTrue(sony.contains { $0.id == "sony_wf1000xm5" })
        XCTAssertNotNil(byID("sennheiser_hd600"))
    }
}

// MARK: - Audio DSP math

final class AudioDspTests: XCTestCase {
    func testEffectiveCurveAppliesNegativeAutoHeadroomEqualToMaxBoostPeak() {
        // Unapplied static-method reference drops argument labels; the contract guarantees the
        // parameter order/return shape mirrors Android's AudioDspMath.computeEffectiveCurve.
        let compute = AudioDsp.computeEffectiveCurve
        let curve = compute("sennheiser_hd600", [], 0, 0, 0, true)
        XCTAssertEqual(curve.totalBandsDb10.count, 10)
        let peak = curve.totalBandsDb10.max() ?? 0
        XCTAssertEqual(peak, curve.autoHeadroomCutDb, accuracy: 0.01)
        XCTAssertEqual(-peak, curve.effectivePreampDb, accuracy: 0.01)
    }

    func testChannelBalanceAttenuatesOppositeChannelWithoutBoostingOverPreamp() {
        let gains = AudioDsp.channelGainsDb
        let (lCenter, rCenter) = gains(-2.0, 0)
        XCTAssertEqual(-2.0, lCenter, accuracy: 0.001)
        XCTAssertEqual(-2.0, rCenter, accuracy: 0.001)

        let (lRight, rRight) = gains(-2.0, 1.0)
        XCTAssertEqual(-2.0, rRight, accuracy: 0.001)
        XCTAssertLessThan(lRight, -15.0)
    }
}

// MARK: - Click wheel geometry

final class ClickWheelMathTests: XCTestCase {
    /// Pattern matching avoids depending on an Equatable conformance for ClickWheelZone.
    private func assertZone(
        _ actual: ClickWheelZone,
        is expected: ClickWheelZone,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let matches: Bool
        switch (expected, actual) {
        case (.outside, .outside),
             (.centerSelect, .centerSelect),
             (.ringTopMenu, .ringTopMenu),
             (.ringRightNext, .ringRightNext),
             (.ringBottomPlay, .ringBottomPlay),
             (.ringLeftPrev, .ringLeftPrev):
            matches = true
        default:
            matches = false
        }
        XCTAssertTrue(matches, "expected \(expected), got \(actual)", file: file, line: line)
    }

    func testShortestAngleHandlesMinusPiToPiWrapCleanly() {
        let delta = ClickWheelMath.shortestAngleDelta
        let prev = Float.pi - 0.1
        let next = -Float.pi + 0.1
        XCTAssertEqual(delta(prev, next), 0.2, accuracy: 0.001)

        let revDelta = delta(next, prev)
        XCTAssertEqual(revDelta, -0.2, accuracy: 0.001)
    }

    func testDetentAccumulatorEmitsClicksAndPreservesRemainder() {
        let accumulate = ClickWheelMath.accumulateClicks
        let step: Float = 0.5
        let (clicks1, rem1) = accumulate(0, 1.2, step)
        XCTAssertEqual(clicks1, 2)
        XCTAssertEqual(rem1, 0.2, accuracy: 0.001)

        let (clicks2, rem2) = accumulate(rem1, -0.8, step)
        XCTAssertEqual(clicks2, -1)
        XCTAssertEqual(rem2, -0.1, accuracy: 0.001)
    }

    func testZoneClassificationSeparatesCenterAndCardinalRingQuadrants() {
        let zone = ClickWheelMath.zoneAt
        assertZone(zone(100, 100, 100, 0.36, 105, 105), is: .centerSelect)
        assertZone(zone(100, 100, 100, 0.36, 100, 20), is: .ringTopMenu)
        assertZone(zone(100, 100, 100, 0.36, 180, 100), is: .ringRightNext)
        assertZone(zone(100, 100, 100, 0.36, 100, 180), is: .ringBottomPlay)
        assertZone(zone(100, 100, 100, 0.36, 20, 100), is: .ringLeftPrev)
        assertZone(zone(100, 100, 100, 0.36, 250, 100), is: .outside)
    }
}
