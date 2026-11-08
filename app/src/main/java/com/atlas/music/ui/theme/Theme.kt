package com.atlas.music.ui.theme

// Atlas Music — deep observatory console (brief)
// Primary task: tap → hear your own files in <2s, no account, no ads.
// Composition: full-bleed background (0 planes) + boxed hero (1 plane: Resume) + ruled rows (everything else).
// Boxed systems (2): Resume hero, mini-player bar. Everything else is ruled rows.
// Radius tokens (3): SM=10dp (art thumbnails/controls), LG=16dp (rail cards), XL=24dp (Resume/cover/NowPlaying). Pill=999dp only for badge/chip.
// Type: Space Grotesk (display) + JetBrains Mono (metadata). Lineage: last project used Inter+Georgia+Mono — this uses Grotesk+Mono because cartographic precision needs a technical grotesk, not an editorial serif.
// Motion: "MERIDIAN SETTLE" — artwork+arc spring Medium (stiffness 180, damping 18), gold dot rides progress. Stagger VECTOR: center-out for Jump Back rail, top-to-bottom for rows. Ease-out imports, ease-in only off-screen.
// Accent job: gold = action + signal only. No other hue.


import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.atlas.music.di.AtlasSettings
import com.atlas.music.util.ThemeMode

private val AtlasDarkScheme = darkColorScheme(
    background = AtlasDark.Background,
    surface = AtlasDark.Surface,
    surfaceVariant = AtlasDark.SurfaceElevated,
    surfaceContainer = AtlasDark.Surface,
    surfaceContainerLow = AtlasDark.Background,
    surfaceContainerHigh = AtlasDark.SurfaceElevated,
    surfaceContainerHighest = AtlasDark.SurfaceHighest,
    onBackground = AtlasDark.OnBackground,
    onSurface = AtlasDark.OnBackground,
    onSurfaceVariant = AtlasDark.OnSurfaceMuted,
    primary = AtlasDark.Accent,
    onPrimary = AtlasDark.OnAccent,
    secondary = AtlasDark.Accent,
    onSecondary = AtlasDark.OnAccent,
    secondaryContainer = AtlasDark.SurfaceHighest,
    onSecondaryContainer = AtlasDark.AccentBright,
    error = AtlasDark.Error,
    outline = AtlasDark.SurfaceHighest,
    outlineVariant = AtlasDark.SurfaceElevated,
)

private val AtlasLightScheme = lightColorScheme(
    background = AtlasLight.Background,
    surface = AtlasLight.Surface,
    surfaceVariant = AtlasLight.SurfaceElevated,
    surfaceContainer = AtlasLight.Surface,
    surfaceContainerLow = AtlasLight.Background,
    surfaceContainerHigh = AtlasLight.SurfaceElevated,
    surfaceContainerHighest = AtlasLight.SurfaceHighest,
    onBackground = AtlasLight.OnBackground,
    onSurface = AtlasLight.OnBackground,
    onSurfaceVariant = AtlasLight.OnSurfaceMuted,
    primary = AtlasLight.Accent,
    onPrimary = AtlasLight.OnAccent,
    secondary = AtlasLight.Accent,
    onSecondary = AtlasLight.OnAccent,
    secondaryContainer = AtlasLight.SurfaceHighest,
    onSecondaryContainer = AtlasLight.AccentDim,
    error = AtlasLight.Error,
    outline = AtlasLight.SurfaceHighest,
    outlineVariant = AtlasLight.SurfaceElevated,
)


// ── me-when-music presets (style.css) ───────────────────────────────
// base/mantle/surface/text/sub/dim + palette blue/mauve/teal/green/yellow/peach/red
private data class Pal(
    val base: androidx.compose.ui.graphics.Color, val mantle: androidx.compose.ui.graphics.Color,
    val s0: androidx.compose.ui.graphics.Color, val s1: androidx.compose.ui.graphics.Color, val s2: androidx.compose.ui.graphics.Color,
    val text: androidx.compose.ui.graphics.Color, val sub: androidx.compose.ui.graphics.Color, val dim: androidx.compose.ui.graphics.Color,
    val blue: androidx.compose.ui.graphics.Color, val mauve: androidx.compose.ui.graphics.Color, val green: androidx.compose.ui.graphics.Color,
    val yellow: androidx.compose.ui.graphics.Color, val peach: androidx.compose.ui.graphics.Color, val red: androidx.compose.ui.graphics.Color, val teal: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
)
private fun c(hex: String) = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
private fun palFrom(token: String): Pal {
    // Inline per-theme tokens (from style.css)
    return when (token) {
        "latte" -> Pal(c("#EFF1F5"),c("#E6E9EF"),c("#CCD0DA"),c("#BCC0CC"),c("#ACB0BE"),c("#4C4F69"),c("#5C5F77"),c("#8C8FA1"),c("#1E66F5"),c("#8839EF"),c("#40A02B"),c("#DF8E1D"),c("#FE640B"),c("#D20F39"),c("#179299"),c("#BCC0CC"))
        "gruvbox" -> Pal(c("#282828"),c("#1D2021"),c("#3C3836"),c("#504945"),c("#665C54"),c("#EBDBB2"),c("#D5C4A1"),c("#928374"),c("#83A598"),c("#B16286"),c("#B8BB26"),c("#FABD2F"),c("#FE8019"),c("#FB4934"),c("#8EC07C"),c("#504945"))
        "tokyonight" -> Pal(c("#1A1B26"),c("#16161E"),c("#24283B"),c("#414868"),c("#565F89"),c("#C0CAF5"),c("#A9B1D6"),c("#565F89"),c("#7AA2F7"),c("#BB9AF7"),c("#9ECE6A"),c("#E0AF68"),c("#FF9E64"),c("#F7768E"),c("#7DCFFF"),c("#2F354D"))
        "nord" -> Pal(c("#2E3440"),c("#242933"),c("#3B4252"),c("#434C5E"),c("#4C566A"),c("#ECEFF4"),c("#D8DEE9"),c("#7B88A1"),c("#81A1C1"),c("#B48EAD"),c("#A3BE8C"),c("#EBCB8B"),c("#D08770"),c("#BF616A"),c("#88C0D0"),c("#434C5E"))
        "dracula" -> Pal(c("#282A36"),c("#21222C"),c("#343746"),c("#44475A"),c("#6272A4"),c("#F8F8F2"),c("#E2E2DC"),c("#8B8FA8"),c("#8BE9FD"),c("#BD93F9"),c("#50FA7B"),c("#F1FA8C"),c("#FFB86C"),c("#FF5555"),c("#8BE9FD"),c("#44475A"))
        "kanagawa" -> Pal(c("#1F1F28"),c("#16161D"),c("#2A2A37"),c("#363646"),c("#54546D"),c("#DCD7BA"),c("#C8C093"),c("#727169"),c("#7E9CD8"),c("#957FB8"),c("#98BB6C"),c("#E6C384"),c("#FF9E3B"),c("#E46876"),c("#7AA89F"),c("#363646"))
        "rosepine" -> Pal(c("#191724"),c("#1F1D2E"),c("#26233A"),c("#403D52"),c("#524F67"),c("#E0DEF4"),c("#908CAA"),c("#6E6A86"),c("#9CCFD8"),c("#EBBCBA"),c("#9CCFD8"),c("#F6C177"),c("#EBBCBA"),c("#EB6F92"),c("#3E8FB0"),c("#403D52"))
        "everforest" -> Pal(c("#2D353B"),c("#232A2E"),c("#343F44"),c("#3D484D"),c("#475258"),c("#D3C6AA"),c("#C5B896"),c("#859289"),c("#7FBBB3"),c("#D699B6"),c("#A7C080"),c("#DBBC7F"),c("#E69875"),c("#E67E80"),c("#83C092"),c("#3D484D"))
        "oxocarbon" -> Pal(c("#161616"),c("#0F0F0F"),c("#262626"),c("#393939"),c("#525252"),c("#DDE1E6"),c("#C1C7CD"),c("#6F6F6F"),c("#82CFFF"),c("#BE95FF"),c("#42BE65"),c("#E5B567"),c("#FF7EB6"),c("#EE5396"),c("#3DDBD9"),c("#393939"))
        else -> Pal(c("#11111B"),c("#181825"),c("#1E1E2E"),c("#313244"),c("#45475A"),c("#CDD6F4"),c("#A6ADC8"),c("#6C7086"),c("#89B4FA"),c("#CBA6F7"),c("#A6E3A1"),c("#F9E2AF"),c("#FAB387"),c("#F38BA8"),c("#94E2D5"),c("#313244"))
    }
}
private fun presetColorScheme(mode: ThemeMode): androidx.compose.material3.ColorScheme {
    val isLight = mode == ThemeMode.LATTE // only light preset
    val id = when (mode) {
        ThemeMode.MOCHA -> "mocha"; ThemeMode.LATTE -> "latte"; ThemeMode.GRUVBOX -> "gruvbox"; ThemeMode.TOKYO_NIGHT -> "tokyonight"
        ThemeMode.NORD -> "nord"; ThemeMode.DRACULA -> "dracula"; ThemeMode.KANAGAWA -> "kanagawa"; ThemeMode.ROSE_PINE -> "rosepine"
        ThemeMode.EVERFOREST -> "everforest"; ThemeMode.OXOCARBON -> "oxocarbon"
        else -> "mocha"
    }
    val p = palFrom(id)
    val primary = p.blue; val onPrimary = if (isLight) c("#EFF1F5") else c("#11111B")
    return if (isLight) lightColorScheme(
        background = p.base, surface = p.mantle, surfaceVariant = p.s0, surfaceContainer = p.mantle,
        surfaceContainerLow = p.base, surfaceContainerHigh = p.s1, surfaceContainerHighest = p.s2,
        onBackground = p.text, onSurface = p.text, onSurfaceVariant = p.sub,
        primary = primary, onPrimary = onPrimary, secondary = p.teal, onSecondary = onPrimary,
        secondaryContainer = p.s1, onSecondaryContainer = p.text, error = p.red,
        outline = p.border, outlineVariant = p.s0
    ) else darkColorScheme(
        background = p.base, surface = p.mantle, surfaceVariant = p.s0, surfaceContainer = p.mantle,
        surfaceContainerLow = p.base, surfaceContainerHigh = p.s1, surfaceContainerHighest = p.s2,
        onBackground = p.text, onSurface = p.text, onSurfaceVariant = p.sub,
        primary = primary, onPrimary = onPrimary, secondary = p.teal, onSecondary = onPrimary,
        secondaryContainer = p.s1, onSecondaryContainer = p.text, error = p.red,
        outline = p.border, outlineVariant = p.s0
    )
}

@Composable
fun AtlasTheme(settings: AtlasSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK, ThemeMode.MOCHA, ThemeMode.GRUVBOX, ThemeMode.TOKYO_NIGHT, ThemeMode.NORD,
        ThemeMode.DRACULA, ThemeMode.KANAGAWA, ThemeMode.ROSE_PINE, ThemeMode.EVERFOREST, ThemeMode.OXOCARBON -> true
        ThemeMode.LIGHT, ThemeMode.LATTE -> false
    }
    val context = LocalContext.current
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        // Presets win over dynamic (explicit user pick)
        settings.themeMode in listOf(ThemeMode.MOCHA, ThemeMode.LATTE, ThemeMode.GRUVBOX, ThemeMode.TOKYO_NIGHT, ThemeMode.NORD, ThemeMode.DRACULA, ThemeMode.KANAGAWA, ThemeMode.ROSE_PINE, ThemeMode.EVERFOREST, ThemeMode.OXOCARBON) -> presetColorScheme(settings.themeMode)
        dynamic && useDark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        useDark -> AtlasDarkScheme
        else -> AtlasLightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AtlasTypography,
        content = content,
    )
}
