package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Draws a clear border when the element holds D-pad focus. Attach to any
 * focusable row/button so non-touch users can see where they are.
 *
 * Pass a shared [interactionSource] when the same element already has clickable
 * behaviour, so focus and click state stay in sync.
 */
fun Modifier.focusHighlight(
    interactionSource: MutableInteractionSource? = null,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    makeFocusable: Boolean = false,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    this
        .clip(shape)
        .border(2.dp, color, shape)
        .then(if (makeFocusable) Modifier.focusable(interactionSource = source) else Modifier)
}
