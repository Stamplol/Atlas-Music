// Atlas iOS — Deterministic procedural cover art (FNV-1a / mulberry32 / 12 Canvas variants).
import Foundation
import SwiftUI

/// Faithful port of Android `CoverArtGenerator` + `ProceduralCoverArt`.
/// Seeds are content/identity derived, so the same track always paints the same cover.
enum CoverArtKit {
    // MARK: - Hashing / PRNG (Kotlin Int overflow is intentional — use wrapping operators)

    /// FNV-1a over UTF-16 code units, matching Kotlin `for (ch in s) { h = h xor ch.code; h *= 16777619 }`.
    static func hashStr(_ s: String) -> Int {
        var h: UInt32 = 2166136261 // 0x811C9DC5 == -375076303 as signed Int32
        for unit in s.utf16 {
            h ^= UInt32(unit)
            h = h &* 16777619
        }
        return Int(Int32(bitPattern: h))
    }

    /// mulberry32 — identical PRNG to the web/Android project, deterministic per seed.
    static func mulberry32(_ seed: Int) -> () -> Double {
        var a = UInt32(bitPattern: Int32(truncatingIfNeeded: seed))
        return {
            a = a &+ 0x6D2B79F5
            var t = a
            t = (t ^ (t >> 15)) &* (t | 1)
            t = (t &+ ((t ^ (t >> 7)) &* (t | 61))) ^ t
            return Double((t ^ (t >> 14)) >> 0) / 4294967296.0
        }
    }

    /// Android spelling alias.
    static func mulberry(_ seed: Int) -> () -> Double { mulberry32(seed) }

    static let AUTO_COVERS: [CoverVariant] = [
        .geo, .rings, .grid, .diag, .dunes, .aurora,
        .meridian, .arcs, .noise, .spark, .horizon, .orbit,
    ]
    static let autoCovers = AUTO_COVERS

    static func variantForSeed(_ seed: Int) -> CoverVariant {
        let n = AUTO_COVERS.count
        return AUTO_COVERS[((seed % n) + n) % n]
    }

    // MARK: - Catppuccin Mocha palette (same as me-when-music / Android)

    private static func color(_ argb: UInt32) -> Color {
        Color(
            red: Double((argb >> 16) & 0xFF) / 255.0,
            green: Double((argb >> 8) & 0xFF) / 255.0,
            blue: Double(argb & 0xFF) / 255.0,
            opacity: Double((argb >> 24) & 0xFF) / 255.0
        )
    }

    static let PALETTE: [Color] = [
        color(0xFF89B4FA), // blue
        color(0xFF94E2D5), // teal
        color(0xFFCBA6F7), // mauve
        color(0xFFA6E3A1), // green
        color(0xFFF9E2AF), // yellow
        color(0xFFFAB387), // peach
        color(0xFFF38BA8), // red
    ]
    static let palette = PALETTE
    static let MANTLE = color(0xFF181825)
    static let mantle = MANTLE
    static let BORDER = color(0xFF313244)
    static let border = BORDER
    static let TEXT_COLOR = color(0xFFCDD6F4)
    static let textColor = TEXT_COLOR
    static let DIM = color(0xFF6C7086)
    static let dim = DIM

    // MARK: - Seeds

    /// Stable seed from track identity (uri + size + duration). iOS has no file-content
    /// ContentResolver equivalent worth the I/O here; identity hashing is deterministic and cheap.
    static func seed(for track: Track) -> Int {
        hashStr("\(track.uri)|\(track.sizeBytes)|\(track.durationMs)")
    }

    /// For previews where no file exists — deterministic from title + size.
    static func seedForPreview(title: String, sizeBytes: Int64) -> Int {
        hashStr("\(title)|\(sizeBytes)")
    }
}

// MARK: - SwiftUI view

/// Deterministic procedural cover with 12 variants and an optional bottom metadata strip.
struct ProceduralCoverView: View {
    let seed: Int
    let title: String
    let ext: String
    let sizeBytes: Int64
    var showStrip: Bool = true

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                CoverArtKit.mantle

                Canvas { ctx, size in
                    draw(ctx: ctx, size: size)
                }

                if showStrip {
                    VStack(spacing: 0) {
                        Rectangle()
                            .fill(CoverArtKit.PALETTE[mod(seed, CoverArtKit.PALETTE.count)].opacity(0.9))
                            .frame(height: 2)
                        HStack(spacing: 8) {
                            Text(leadingCharacter.uppercased())
                                .font(.system(size: 24, weight: .heavy, design: .rounded))
                                .foregroundColor(CoverArtKit.textColor)
                            Text("  \(cleanExt) · \(Format.bytes(sizeBytes))")
                                .font(.system(size: 10, weight: .medium, design: .monospaced))
                                .foregroundColor(CoverArtKit.dim)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                    }
                    .background(CoverArtKit.mantle.opacity(0.88))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
    }

    private var leadingCharacter: String {
        title.trimmingCharacters(in: .whitespacesAndNewlines).first.map { String($0) } ?? "—"
    }

    private var cleanExt: String {
        ext.replacingOccurrences(of: ".", with: "").uppercased()
    }

    private func mod(_ a: Int, _ b: Int) -> Int { ((a % b) + b) % b }

    // MARK: Canvas painting

    private func draw(ctx: GraphicsContext, size: CGSize) {
        let W = size.width
        let H = size.height
        guard W > 0, H > 0 else { return }

        let minWH = min(W, H)
        var rnd = CoverArtKit.mulberry32(seed)
        let pal = CoverArtKit.PALETTE
        let base = CoverArtKit.mantle
        let border = CoverArtKit.border
        let textColor = CoverArtKit.textColor
        let dim = CoverArtKit.dim
        let variant = CoverArtKit.variantForSeed(seed)

        func col(_ idx: Int) -> Color { pal[mod(seed + idx, pal.count)] }
        func line(_ a: CGPoint, _ b: CGPoint, _ c: Color, _ w: CGFloat) {
            var p = Path()
            p.move(to: a)
            p.addLine(to: b)
            ctx.stroke(p, with: .color(c), lineWidth: w)
        }
        func roundLine(_ a: CGPoint, _ b: CGPoint, _ c: Color, _ w: CGFloat) {
            var p = Path()
            p.move(to: a)
            p.addLine(to: b)
            ctx.stroke(p, with: .color(c), style: StrokeStyle(lineWidth: w, lineCap: .round))
        }
        func circle(_ center: CGPoint, _ r: CGFloat, _ c: Color,
                    stroke: Bool = false, _ w: CGFloat = 2.5) {
            let p = Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r,
                                           width: r * 2, height: r * 2))
            if stroke { ctx.stroke(p, with: .color(c), lineWidth: w) }
            else { ctx.fill(p, with: .color(c)) }
        }
        func box(_ origin: CGPoint, _ s: CGSize, _ c: Color,
                 stroke: Bool = false, _ w: CGFloat = 1.5) {
            let p = Path(CGRect(origin: origin, size: s))
            if stroke { ctx.stroke(p, with: .color(c), lineWidth: w) }
            else { ctx.fill(p, with: .color(c)) }
        }
        func oval(_ rect: CGRect, _ c: Color, _ w: CGFloat = 2) {
            ctx.stroke(Path(ellipseIn: rect), with: .color(c), lineWidth: w)
        }

        switch variant {
        case .geo:
            var y: CGFloat = 10
            while y < H {
                var x: CGFloat = 10
                while x < W {
                    box(CGPoint(x: x, y: y), CGSize(width: 1.5, height: 1.5), border)
                    x += 18
                }
                y += 18
            }
            for i in 0..<5 {
                let c = col(i * 2)
                let kind = Int(rnd() * 4)
                let x = CGFloat(rnd()) * W
                let y = CGFloat(rnd()) * H
                let r = CGFloat(18.0 + rnd() * 70.0)
                let alpha: Double = kind == 1 ? 0.28 : 0.9
                switch kind {
                case 0:
                    circle(CGPoint(x: x, y: y), r, c.opacity(alpha), stroke: true, 2.5)
                case 1:
                    circle(CGPoint(x: x, y: y), r, c.opacity(alpha))
                case 2:
                    line(CGPoint(x: x - r, y: y + r), CGPoint(x: x + r, y: y - r), c.opacity(alpha), 2.5)
                    line(CGPoint(x: x - r + 8, y: y + r), CGPoint(x: x + r + 8, y: y - r), c.opacity(0.4), 2.5)
                case 3:
                    box(CGPoint(x: x - r / 2, y: y - r / 2), CGSize(width: r, height: r), c.opacity(0.85), stroke: true, 2.5)
                default:
                    for k in 0..<4 {
                        circle(CGPoint(x: x + CGFloat(k) * 14 - 20, y: y), 3.5, c.opacity(0.9))
                    }
                }
            }

        case .rings:
            let cx = W * CGFloat(0.3 + rnd() * 0.4)
            let cy = H * CGFloat(0.3 + rnd() * 0.35)
            for i in stride(from: 6, through: 1, by: -1) {
                let c = col(i)
                let radius = CGFloat(i) * minWH / 7.5
                let alpha: Double = i == 6 ? 0.9 : 0.55
                let width: CGFloat = i == 6 ? 5 : 2
                circle(CGPoint(x: cx, y: cy), radius, c.opacity(alpha), stroke: true, width)
            }
            for _ in 0..<24 {
                let a = 0.25 + rnd() * 0.6
                box(CGPoint(x: CGFloat(rnd()) * W, y: CGFloat(rnd()) * H),
                    CGSize(width: 2.5, height: 2.5), col(mod(seed, pal.count)).opacity(a))
            }

        case .grid:
            let n = 7
            let s = W / CGFloat(n)
            for gy in 0..<n {
                for gx in 0..<n {
                    let r = rnd()
                    if r < 0.42 {
                        let c = pal[mod(gx * 3 + gy * 5 + seed, pal.count)]
                        let a = 0.35 + rnd() * 0.55
                        box(CGPoint(x: CGFloat(gx) * s + 2, y: CGFloat(gy) * s + 2),
                            CGSize(width: s - 4, height: s - 4), c.opacity(a))
                    } else if r < 0.5 {
                        box(CGPoint(x: CGFloat(gx) * s + 2, y: CGFloat(gy) * s + 2),
                            CGSize(width: s - 4, height: s - 4), dim.opacity(0.7), stroke: true, 1.5)
                    }
                }
            }

        case .diag:
            var i = -Int(H)
            while i < Int(W + H) {
                let c = pal[mod(i / 22 + seed, pal.count)]
                line(CGPoint(x: CGFloat(i), y: 0), CGPoint(x: CGFloat(i) + H, y: H), c.opacity(0.55), 3)
                i += 22
            }
            circle(CGPoint(x: W * 0.68, y: H * 0.34), minWH * 0.2, textColor.opacity(0.92), stroke: true, 3)

        case .dunes:
            for r in 0..<4 {
                let c = col(r * 2)
                let baseY = H * CGFloat(0.25 + Double(r) * 0.16)
                let amp = H * 0.05
                let phase = Double(r) * 1.9 + Double(mod(seed, 100))
                var x: CGFloat = 0
                var prevX: CGFloat = 0
                var prevY = baseY + CGFloat(sin(phase) * Double(amp))
                while x <= W {
                    let y = baseY + CGFloat(sin(Double(x) * 0.03 + phase) * Double(amp))
                    if x > 0 {
                        line(CGPoint(x: prevX, y: prevY), CGPoint(x: x, y: y), c.opacity(0.85), 2.5)
                    }
                    prevX = x
                    prevY = y
                    x += 5
                }
            }
            let dotX = W * CGFloat(0.2 + rnd() * 0.6)
            circle(CGPoint(x: dotX, y: H * 0.14), 9, col(3).opacity(0.5))

        case .aurora:
            for r in 0..<5 {
                let c = pal[mod(r + seed, pal.count)]
                let baseY = H * CGFloat(0.28 + Double(r) * 0.13)
                let amp = H * 0.07
                let phase = Double(r) * 1.3 + Double(mod(seed, 20))
                var x: CGFloat = 0
                var prevX: CGFloat = 0
                var prevY = baseY + CGFloat(sin(phase) * Double(amp))
                while x <= W {
                    let y = baseY
                        + CGFloat((sin(Double(x) * 0.04 + phase) + sin(Double(x) * 0.012 - phase * 2) * 0.4) * Double(amp))
                    if x > 0 {
                        line(CGPoint(x: prevX, y: prevY), CGPoint(x: x, y: y), c.opacity(0.6), 8)
                    }
                    prevX = x
                    prevY = y
                    x += 6
                }
            }
            var gy: CGFloat = 10
            while gy < H {
                var gx: CGFloat = 10
                while gx < W {
                    box(CGPoint(x: gx, y: gy), CGSize(width: 1, height: 1), border.opacity(0.5))
                    gx += 22
                }
                gy += 22
            }

        case .meridian:
            let cx = W / 2
            let cy = H / 2
            let r = minWH * 0.34
            circle(CGPoint(x: cx, y: cy), r, col(0).opacity(0.9), stroke: true, 3)
            oval(CGRect(x: cx - r * 0.55, y: cy - r, width: r * 1.1, height: r * 2), col(1).opacity(0.55), 2)
            oval(CGRect(x: cx - r, y: cy - r * 0.55, width: r * 2, height: r * 1.1), col(2).opacity(0.55), 2)

            var star = Path()
            star.move(to: CGPoint(x: cx, y: cy - r * 0.85))
            star.addLine(to: CGPoint(x: cx + r * 0.16, y: cy - r * 0.12))
            star.addLine(to: CGPoint(x: cx + r * 0.85, y: cy))
            star.addLine(to: CGPoint(x: cx + r * 0.16, y: cy + r * 0.12))
            star.addLine(to: CGPoint(x: cx, y: cy + r * 0.85))
            star.addLine(to: CGPoint(x: cx - r * 0.16, y: cy + r * 0.12))
            star.addLine(to: CGPoint(x: cx - r * 0.85, y: cy))
            star.addLine(to: CGPoint(x: cx - r * 0.16, y: cy - r * 0.12))
            star.closeSubpath()
            ctx.fill(star, with: .color(col(3).opacity(0.95)))
            circle(CGPoint(x: cx, y: cy), 6, base)

            for i in 0..<12 {
                let a = CGFloat(rnd() * 360.0 * Double.pi / 180.0)
                circle(CGPoint(x: cx + cos(a) * r, y: cy + sin(a) * r), 2.5, col(i).opacity(0.8))
            }

        case .arcs:
            for i in stride(from: 6, through: 1, by: -1) {
                let c = col(i)
                let radius = CGFloat(i) * minWH / 6.5
                var p = Path()
                p.addArc(center: CGPoint(x: 0, y: H), radius: radius,
                         startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
                ctx.stroke(p, with: .color(c.opacity(0.75)), lineWidth: 4)
            }
            for i in 0..<20 {
                box(CGPoint(x: CGFloat(rnd()) * W, y: CGFloat(rnd()) * H),
                    CGSize(width: 3, height: 3), col(i).opacity(0.4))
            }

        case .noise:
            for _ in 0..<180 {
                let a = 0.25 + rnd() * 0.75
                let r = CGFloat(1.0 + rnd() * 2.5)
                box(CGPoint(x: CGFloat(rnd()) * W, y: CGFloat(rnd()) * H),
                    CGSize(width: r, height: r), pal[Int(rnd() * Double(pal.count))].opacity(a))
            }
            for i in 0..<3 {
                let c = col(i * 2 + 1)
                var x = -H
                while x < W {
                    line(CGPoint(x: x, y: 0), CGPoint(x: x + H * 0.4, y: H), c.opacity(0.35), 2)
                    x += 48
                }
            }

        case .spark:
            let cx = W / 2
            let cy = H / 2
            for i in 0..<16 {
                let a = Double(i) / 16.0 * 2.0 * Double.pi
                let len = minWH * CGFloat(0.28 + rnd() * 0.28)
                let x2 = cx + CGFloat(cos(a)) * len
                let y2 = cy + CGFloat(sin(a)) * len
                roundLine(CGPoint(x: cx, y: cy), CGPoint(x: x2, y: y2), col(i).opacity(0.75), 3)
                circle(CGPoint(x: x2, y: y2), 3, col(i).opacity(0.9))
            }
            circle(CGPoint(x: cx, y: cy), 10, col(0))
            circle(CGPoint(x: cx, y: cy), 4, base)

        case .horizon:
            let sunY = H * 0.62
            let sunR = minWH * 0.22
            let cx = W / 2
            for i in 0..<3 {
                let y = H * CGFloat(0.62 + Double(i) * 0.11)
                line(CGPoint(x: 20, y: y), CGPoint(x: W - 20, y: y), col(i).opacity(0.7), 3)
            }
            circle(CGPoint(x: cx, y: sunY), sunR, col(3).opacity(0.9))
            for i in 0..<30 {
                circle(CGPoint(x: CGFloat(rnd()) * W, y: CGFloat(rnd()) * H), 2, col(i).opacity(0.45))
            }

        case .orbit:
            let cx = W / 2
            let cy = H / 2
            circle(CGPoint(x: cx, y: cy), 9, col(0).opacity(0.9))
            for ring in 1...3 {
                let r = minWH * CGFloat(0.18 + Double(ring) * 0.13)
                circle(CGPoint(x: cx, y: cy), r, col(ring).opacity(0.6), stroke: true, 2)
                let nDots = 3 + ring * 2
                for k in 0..<nDots {
                    let a = Double(k) / Double(nDots) * 2.0 * Double.pi + Double(ring) * 0.7
                    circle(CGPoint(x: cx + CGFloat(cos(a)) * r, y: cy + CGFloat(sin(a)) * r),
                           4, col(ring + k).opacity(0.95))
                }
            }
        }
    }
}
