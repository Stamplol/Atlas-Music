// Atlas iOS — the 310pt tilted meridian orbit with a gold progress dot at its leading edge.
import SwiftUI

struct MeridianArc: View {
    private let progress: Double
    private let size: CGFloat

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(progress: Double, size: CGFloat = 310) {
        self.progress = progress
        self.size = size
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        let fraction = min(max(progress, 0), 1)
        Canvas { context, canvasSize in
            let rx = canvasSize.width * 0.52
            let ry = canvasSize.width * 0.24

            // Start at -90° (top) and sweep clockwise, matching Android drawArc.
            var ellipse = Path()
            ellipse.addArc(
                center: .zero,
                radius: 1,
                startAngle: .degrees(-90),
                endAngle: .degrees(270),
                clockwise: false
            )
            ellipse = ellipse.applying(CGAffineTransform(scaleX: rx, y: ry))

            context.translateBy(x: canvasSize.width / 2, y: canvasSize.height / 2)
            context.rotate(by: .degrees(-10))

            context.stroke(
                ellipse,
                with: .color(c.textMuted.opacity(0.35)),
                style: StrokeStyle(lineWidth: 1.5, lineCap: .butt, dash: [14, 18])
            )

            if fraction > 0 {
                context.stroke(
                    ellipse.trimmedPath(from: 0, to: CGFloat(fraction)),
                    with: .color(c.accent),
                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round)
                )

                let angle = (-90.0 + 360.0 * fraction) * .pi / 180.0
                let dot = CGPoint(x: cos(angle) * rx, y: sin(angle) * ry)
                context.fill(
                    Path(ellipseIn: CGRect(x: dot.x - 4, y: dot.y - 4, width: 8, height: 8)),
                    with: .color(c.accent)
                )
            }
        }
        .frame(width: size, height: size)
        .accessibilityLabel("Playback progress \(Int(fraction * 100))%")
    }
}
