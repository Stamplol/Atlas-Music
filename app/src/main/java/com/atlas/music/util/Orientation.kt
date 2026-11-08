package com.atlas.music.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun isLandscape(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        (cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenWidthDp > 600)
}
