// Atlas iOS — Widget Extension entry point. The app target has its own @main in
// AtlasMusicApp.swift; a Widget Extension needs a separate @main/WidgetBundle.
import WidgetKit

@main
struct AtlasMusicWidgetBundle: WidgetBundle {
    var body: some Widget {
        // The concrete widget lives in the shared Widgets/AtlasWidgets.swift file, which is a
        // member of both targets. Returning it directly avoids nesting one WidgetBundle in another.
        AtlasMusicWidget()
    }
}
