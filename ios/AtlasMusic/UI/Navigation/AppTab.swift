// Atlas iOS — the five primary tabs shown in RootView's TabView.
import Foundation

enum AppTab: String, CaseIterable, Identifiable {
    case home, library, search, tools, settings

    var id: String { rawValue }

    var systemName: String {
        switch self {
        case .home: return "house.fill"
        case .library: return "music.note.list"
        case .search: return "magnifyingglass"
        case .tools: return "wrench.and.screwdriver.fill"
        case .settings: return "gearshape.fill"
        }
    }

    var label: String {
        switch self {
        case .home: return "Home"
        case .library: return "Library"
        case .search: return "Search"
        case .tools: return "Tools"
        case .settings: return "Settings"
        }
    }
}
