// Atlas iOS — Full-screen animated background engine (14 distinct Canvas modes, ~30fps).
import Foundation
import SwiftUI

private let atlasGold = Color(
    red: Double(0xE5) / 255.0,
    green: Double(0xA8) / 255.0,
    blue: Double(0x3D) / 255.0
)

private func bgColor(_ argb: UInt32) -> Color {
    Color(
        red: Double((argb >> 16) & 0xFF) / 255.0,
        green: Double((argb >> 8) & 0xFF) / 255.0,
        blue: Double(argb & 0xFF) / 255.0,
        opacity: Double((argb >> 24) & 0xFF) / 255.0
    )
}

/// SwiftUI port of Android `AnimatedBackground`. Each mode is a deterministic function of
/// the timeline clock, so no mutable simulation state leaks across recompositions. Reduced
/// motion stops the timeline entirely and paints a static first frame.
struct AnimatedBackground: View {
    let mode: BackgroundMode
    let density: Int
    let speed: Int
    let trail: Int
    let react: Bool
    let isPlaying: Bool
    let accent: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(mode: BackgroundMode = .wave, density: Int = 45, speed: Int = 55, trail: Int = 72,
         react: Bool = true, isPlaying: Bool = false, accent: Color = atlasGold) {
        self.mode = mode
        self.density = density
        self.speed = speed
        self.trail = trail
        self.react = react
        self.isPlaying = isPlaying
        self.accent = accent
    }

    var body: some View {
        Group {
            if mode == .off {
                Rectangle().fill(CoverArtKit.mantle)
            } else if reduceMotion {
                Canvas { ctx, size in render(ctx, size, t: 0) }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
                    Canvas { ctx, size in
                        render(ctx, size, t: timeline.date.timeIntervalSinceReferenceDate)
                    }
                }
            }
        }
        .ignoresSafeArea()
    }

    private func render(_ ctx: GraphicsContext, _ size: CGSize, t: Double) {
        let W = size.width
        let H = size.height
        guard W > 0, H > 0 else { return }

        let full = Path(CGRect(x: 0, y: 0, width: W, height: H))
        ctx.fill(full, with: .color(CoverArtKit.mantle))

        let pal = CoverArtKit.PALETTE
        let clock = t.truncatingRemainder(dividingBy: 10_000.0)
        let energy: Double = !react ? 0 : (isPlaying ? min(1, max(0, 0.55 + 0.4 * sin(clock * 2))) : 0.1)
        let speedFactor = 0.4 + Double(speed) / 100.0 * 1.6
        let animT = clock * speedFactor * (isPlaying ? 1.35 : 0.6)
        let visibility = max(0.3, 1.0 - Double(trail) / 100.0 * 0.5)

        func vis(_ a: Double) -> Double { max(0, min(1, a * visibility)) }
        func idx(_ n: Int) -> Color { pal[((n % pal.count) + pal.count) % pal.count] }
        func line(_ a: CGPoint, _ b: CGPoint, _ c: Color, _ w: CGFloat) {
            var p = Path()
            p.move(to: a)
            p.addLine(to: b)
            ctx.stroke(p, with: .color(c), lineWidth: w)
        }
        func rect(_ r: CGRect, _ c: Color) { ctx.fill(Path(r), with: .color(c)) }
        func circ(_ center: CGPoint, _ r: CGFloat, _ c: Color) {
            ctx.fill(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r,
                                            width: r * 2, height: r * 2)), with: .color(c))
        }

        switch mode {
        case .off:
            break

        case .conway:
            let cell: CGFloat = 12
            let cols = max(1, Int(ceil(Double(W) / 12.0)))
            let rows = max(1, Int(ceil(Double(H) / 12.0)))
            let gen = Int(animT * 1.5)
            let threshold = Int(12 + Double(density) / 100.0 * 20 + energy * 20)
            for gy in 0..<rows {
                for gx in 0..<cols {
                    let h = hash3(gx, gy, gen)
                    if Int(h % 100) < threshold {
                        rect(CGRect(x: CGFloat(gx) * cell + 1, y: CGFloat(gy) * cell + 1,
                                    width: cell - 2, height: cell - 2),
                             idx(gx * 7 + gy * 13).opacity(vis(0.5 + energy * 0.4)))
                    }
                }
            }

        case .boids:
            let n = 50 + Int(Double(density) / 100.0 * 170.0)
            for i in 0..<n {
                let phase = Double(hash3(i, 0, 0) % 1000) / 1000.0 * 2.0 * Double.pi
                let mul = 0.5 + Double(hash3(i, 1, 0) % 100) / 100.0
                let x = W * CGFloat(fract(0.5 + 0.45 * sin(animT * 0.5 * mul + phase)))
                let y = H * CGFloat(fract(0.5 + 0.45 * cos(animT * 0.42 * mul + phase * 1.7)))
                let a = animT * 0.5 + phase
                let dx = cos(a), dy = sin(a), px = -dy, py = dx
                var p = Path()
                p.move(to: CGPoint(x: x + CGFloat(dx * 7), y: y + CGFloat(dy * 7)))
                p.addLine(to: CGPoint(x: x - CGFloat(dx * 4) + CGFloat(px * 3.6),
                                      y: y - CGFloat(dy * 4) + CGFloat(py * 3.6)))
                p.addLine(to: CGPoint(x: x - CGFloat(dx * 4) - CGFloat(px * 3.6),
                                      y: y - CGFloat(dy * 4) - CGFloat(py * 3.6)))
                p.closeSubpath()
                ctx.fill(p, with: .color(idx(i).opacity(vis(0.85))))
            }

        case .wave:
            let rows = 5
            let ampBase = H * CGFloat(0.055 * (0.5 + Double(density) / 90.0))
            for r in 0..<rows {
                let yBase = H * CGFloat(Double(r + 1) / Double(rows + 1))
                let c = idx(r)
                var x: CGFloat = 0
                var prevX: CGFloat = 0
                let reactBoost = react ? 1 + energy * 1.6 : 1
                func yAt(_ px: CGFloat) -> CGFloat {
                    yBase
                        + CGFloat(sin(Double(px) * 0.008 + animT * (1 + Double(r) * 0.22) + Double(r) * 1.7)) * ampBase
                        + CGFloat(sin(Double(px) * 0.02 - animT * 1.6)) * ampBase * 0.28 * CGFloat(reactBoost)
                }
                var prevY = yAt(0)
                while x <= W {
                    let y = yAt(x)
                    line(CGPoint(x: prevX, y: prevY), CGPoint(x: x, y: y), c.opacity(vis(0.55)), 1.6)
                    prevX = x
                    prevY = y
                    x += 6
                }
            }
            let gap: CGFloat = 34
            var gx = gap / 2
            while gx < W {
                var gy = gap / 2
                while gy < H {
                    let d = sin(Double(gx) * 0.012 + animT * 2) * cos(Double(gy) * 0.012 - animT * 1.4)
                    if d > 0.86 {
                        rect(CGRect(x: gx, y: gy, width: 2, height: 2),
                             idx(Int(gx + gy)).opacity(vis(0.5)))
                    }
                    gy += gap
                }
                gx += gap
            }

        case .maze:
            let cw: CGFloat = 30
            let cols = max(8, Int(W / cw))
            let rows = max(8, Int(H / cw))
            let ox = (W - CGFloat(cols) * cw) / 2
            let oy = (H - CGFloat(rows) * cw) / 2
            let wall = bgColor(0xFF89B4FA)
            for cy in 0..<rows {
                for cx in 0..<cols {
                    if hash3(cx, cy, 0) % 100 >= 80 { continue }
                    let x = ox + CGFloat(cx) * cw
                    let y = oy + CGFloat(cy) * cw
                    let w = hash3(cx, cy, 1)
                    if w & 1 != 0 { line(CGPoint(x: x, y: y), CGPoint(x: x + cw, y: y), wall.opacity(vis(0.5)), 1) }
                    if w & 8 != 0 { line(CGPoint(x: x, y: y), CGPoint(x: x, y: y + cw), wall.opacity(vis(0.5)), 1) }
                    if cx == cols - 1 && w & 2 != 0 {
                        line(CGPoint(x: x + cw, y: y), CGPoint(x: x + cw, y: y + cw), wall.opacity(vis(0.5)), 1)
                    }
                    if cy == rows - 1 && w & 4 != 0 {
                        line(CGPoint(x: x, y: y + cw), CGPoint(x: x + cw, y: y + cw), wall.opacity(vis(0.5)), 1)
                    }
                }
            }
            let total = max(1, cols * rows)
            let agent = Int(animT * 2) % total
            let ax = ox + CGFloat(agent % cols) * cw + cw / 2
            let ay = oy + CGFloat(agent / cols) * cw + cw / 2
            circ(CGPoint(x: ax, y: ay), 5, bgColor(0xFFF9E2AF).opacity(vis(0.9)))
            rect(CGRect(x: ox + cw / 2 - 3, y: oy + cw / 2 - 3, width: 6, height: 6),
                 bgColor(0xFFF38BA8).opacity(vis(0.9)))

        case .sort:
            let n = max(40, min(220, Int(W / 9)))
            let bw = W / CGFloat(n)
            let base = H - 8
            let maxH = H * 0.72
            for k in 0..<n {
                let v = fract(sin(Double(k) * 0.7 + animT * 0.6) * 0.5 + 0.5)
                let h = CGFloat(6) + CGFloat(v) * maxH * CGFloat(1 + energy * 0.25 * sin(Double(k) * 0.3))
                let c: Color
                if (k + Int(animT * 3)) % 7 == 0 { c = bgColor(0xFFA6E3A1) }
                else if k == Int(animT * 5) % n { c = bgColor(0xFFF38BA8) }
                else { c = idx(k) }
                rect(CGRect(x: CGFloat(k) * bw + 1, y: base - h,
                            width: max(1, bw - 2), height: h), c.opacity(vis(0.8)))
            }

        case .starfield:
            let n = 120 + Int(Double(density) / 100.0 * 380.0)
            let cx = W / 2
            let cy = H / 2
            let maxR = CGFloat(hypot(Double(cx), Double(cy)))
            let warp = 0.004 + Double(speed) / 100.0 * 0.022 + energy * 0.035
            for i in 0..<n {
                let angle = Double(hash3(i, 0, 2) % 10000) / 10000.0 * 2.0 * Double.pi
                let phase = Double(hash3(i, 1, 2) % 1000) / 1000.0
                let zPrev = fract(phase + animT * warp)
                let z = fract(zPrev + 0.02)
                let dir = CGPoint(x: CGFloat(cos(angle)), y: CGFloat(sin(angle)))
                line(CGPoint(x: cx + dir.x * maxR * CGFloat(zPrev), y: cy + dir.y * maxR * CGFloat(zPrev)),
                     CGPoint(x: cx + dir.x * maxR * CGFloat(z), y: cy + dir.y * maxR * CGFloat(z)),
                     idx(i).opacity(vis(0.2 + z * 0.8)), CGFloat(0.6 + z * 2))
            }

        case .matrix:
            let fs: CGFloat = 15
            let n = Int(W / fs) + 1
            let chars = Array("アイウエオカキクケコサシスセソタチツテト0123456789ABCDEF$#*+-")
            for i in 0..<n {
                let phase = Double(hash3(i, 0, 3) % 1000) / 1000.0
                let yHead = H * CGFloat(fract(phase + animT * (0.15 + Double(speed) / 100.0 * 0.3)))
                for j in 0..<8 {
                    let y = yHead - CGFloat(j) * fs
                    if y < 0 || y > H { continue }
                    let gi = Int(hash3(i, j, Int(animT * 3)) % UInt32(chars.count))
                    let t = Text(String(chars[gi]))
                        .font(.system(size: fs, design: .monospaced))
                        .foregroundColor(idx(3).opacity(vis(0.9 - Double(j) * 0.1)))
                    ctx.draw(t, at: CGPoint(x: CGFloat(i) * fs, y: y))
                }
            }

        case .flow:
            let n = 150 + Int(Double(density) / 100.0 * 500.0)
            for i in 0..<n {
                let bx = Double(hash3(i, 0, 4) % 1000) / 1000.0
                let by = Double(hash3(i, 1, 4) % 1000) / 1000.0
                let phase = Double(hash3(i, 2, 4) % 1000) / 1000.0 * 2.0 * Double.pi
                let x = W * CGFloat(fract(bx + animT * 0.03 + 0.05 * sin(animT * 0.5 + phase)))
                let y = H * CGFloat(fract(by + animT * 0.02 + 0.05 * cos(animT * 0.4 + phase)))
                let a = (sin(Double(x) * 0.004 + animT) + cos(Double(y) * 0.0045 - animT * 0.7)
                    + sin((Double(x) + Double(y)) * 0.0022 + animT * 0.5)) * Double.pi
                line(CGPoint(x: x, y: y),
                     CGPoint(x: x + CGFloat(cos(a)) * 4, y: y + CGFloat(sin(a)) * 4),
                     idx(Int(abs(a) * 1.4)).opacity(vis(0.7)), 1.4)
            }

        case .lorenz:
            let steps = 4 + Int(Double(speed) / 100.0 * 22.0) + Int(energy * 22)
            let dt = 0.0085
            let scale = CGFloat(min(W, H) / 58.0)
            let cx = W / 2
            let cy = H * 0.74
            var x = 0.1 + sin(animT * 0.1)
            var y = 0.0
            var z = 0.0
            var prev: CGPoint?
            for step in 0..<steps {
                let dx = 10 * (y - x)
                let dy = x * (28 - z) - y
                let dz = x * y - (8.0 / 3.0) * z
                x += dx * dt
                y += dy * dt
                z += dz * dt
                let point = CGPoint(x: cx + CGFloat(x) * scale, y: cy - CGFloat(z) * scale)
                if let prev {
                    line(prev, point, idx(step / 14).opacity(vis(0.85)), max(1.2, min(W, H) / 700))
                }
                prev = point
            }
            if let prev { circ(prev, 3, idx(Int(animT)).opacity(vis(0.9))) }

        case .pendulum:
            let n = 1 + Int(Double(density) / 100.0 * 4.0)
            let L1 = min(W, H) * 0.17
            let L2 = L1 * 0.92
            let cx = W / 2
            let cy = H * 0.4
            for k in 0..<n {
                let a1 = Double.pi / 2 + 0.6 * sin(animT * 1.1 + Double(k) * 0.13)
                let a2 = a1 + 0.5 * sin(animT * 1.7 + Double(k) * 0.3)
                let x1 = cx + L1 * CGFloat(sin(a1))
                let y1 = cy + L1 * CGFloat(cos(a1))
                let x2 = x1 + L2 * CGFloat(sin(a2))
                let y2 = y1 + L2 * CGFloat(cos(a2))
                line(CGPoint(x: cx, y: cy), CGPoint(x: x1, y: y1), idx(k).opacity(vis(0.45)), 1.5)
                line(CGPoint(x: x1, y: y1), CGPoint(x: x2, y: y2), idx(k).opacity(vis(0.45)), 1.5)
                circ(CGPoint(x: x2, y: y2), 4, idx(k).opacity(vis(0.95)))
            }
            circ(CGPoint(x: cx, y: cy), 3, idx(0))

        case .plasma:
            let gw = 56
            let gh = 32
            let cw = W / CGFloat(gw)
            let ch = H / CGFloat(gh)
            let zx = 0.05 + Double(density) / 100.0 * 0.07
            for gy in 0..<gh {
                for gx in 0..<gw {
                    let dx = Double(gx - gw / 2)
                    let dy = Double(gy - gh / 2)
                    let v = sin(Double(gx) * zx * 1.6 + animT)
                        + sin(Double(gy) * zx * 1.9 - animT * 0.7)
                        + sin(Double(gx + gy) * zx + animT * 0.5)
                        + sin(sqrt(dx * dx + dy * dy) * zx * 2 - animT * 1.2)
                    rect(CGRect(x: CGFloat(gx) * cw, y: CGFloat(gy) * ch,
                                width: cw + 1, height: ch + 1),
                         idx(Int(abs(v * 1.2))).opacity(vis(0.95)))
                }
            }

        case .langton:
            let cell: CGFloat = 9
            let cols = max(1, Int(W / cell))
            let rows = max(1, Int(H / cell))
            let gen = Int(animT * 2)
            let threshold = Int(30 + energy * 25)
            for gy in 0..<rows {
                for gx in 0..<cols {
                    if hash3(gx, gy, gen) % 100 < threshold {
                        rect(CGRect(x: CGFloat(gx) * cell, y: CGFloat(gy) * cell,
                                    width: cell, height: cell),
                             idx(gx + gy + gen).opacity(vis(0.8)))
                    }
                }
            }
            let ants = 2 + Int(Double(density) / 100.0 * 4.0)
            for k in 0..<ants {
                let ax = Int(fract(Double(hash3(k, 0, 5) % 1000) / 1000.0 + animT * 0.03) * Double(cols))
                let ay = Int(fract(Double(hash3(k, 1, 5) % 1000) / 1000.0 + animT * 0.027) * Double(rows))
                rect(CGRect(x: CGFloat(ax) * cell + 1, y: CGFloat(ay) * cell + 1,
                            width: cell - 2, height: cell - 2), idx(k).opacity(vis(0.95)))
                rect(CGRect(x: CGFloat(ax) * cell + 3, y: CGFloat(ay) * cell + 3,
                            width: cell - 6, height: cell - 6), Color.white.opacity(vis(0.9)))
            }

        case .dunes:
            for r in 0..<4 {
                let c = idx(r * 2)
                let baseY = H * CGFloat(0.28 + Double(r) * 0.16)
                let amp = H * CGFloat(0.06 * (0.5 + Double(density) / 90.0))
                let phase = Double(r) * 1.9 + animT * (0.4 + Double(r) * 0.08)
                var x: CGFloat = 0
                var prevX: CGFloat = 0
                var prevY = baseY + CGFloat(sin(phase) * Double(amp))
                while x <= W {
                    let y = baseY + CGFloat(sin(Double(x) * 0.03 + phase) * Double(amp))
                    if x > 0 {
                        line(CGPoint(x: prevX, y: prevY), CGPoint(x: x, y: y), c.opacity(vis(0.22)), 2)
                    }
                    prevX = x
                    prevY = y
                    x += 6
                }
            }
            let dotX = W * CGFloat(0.5 + 0.22 * sin(animT * 0.3))
            circ(CGPoint(x: dotX, y: H * 0.15), 10, accent.opacity(vis(0.38)))
        }
    }
}

private func fract(_ x: Double) -> Double { x - floor(x) }

/// Deterministic 32-bit hash (FNV-style, wrapping) for stateless simulation cells.
private func hash3(_ x: Int, _ y: Int, _ z: Int) -> UInt32 {
    var h: UInt32 = 2166136261
    for v in [x, y, z] {
        h ^= UInt32(truncatingIfNeeded: v)
        h = h &* 16777619
    }
    return h
}
