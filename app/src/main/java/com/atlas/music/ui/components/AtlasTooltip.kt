package com.atlas.music.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Touch-first tooltip: shown on long-press / hover. Uses the same wording as the
 * accessibility contentDescription — keep them identical, never duplicate them.
 * Every icon-only control goes through this.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AtlasIconButton(    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) { content() }
    }
}
