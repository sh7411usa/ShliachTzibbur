package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

/**
 * Makes a `verticalScroll` container scrollable with a D-pad: the element takes
 * focus, and up/down (and page up/down) scroll it. Needed for content that has
 * nothing else focusable inside it (e.g. the legal documents).
 */
fun Modifier.dpadScrollable(scrollState: ScrollState, autoFocus: Boolean = true): Modifier = composed {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }

    this
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val delta = when (event.key) {
                Key.DirectionDown -> LINE
                Key.DirectionUp -> -LINE
                Key.PageDown -> PAGE
                Key.PageUp -> -PAGE
                else -> return@onKeyEvent false
            }
            scope.launch { scrollState.animateScrollBy(delta) }
            true
        }
}

private const val LINE = 140f
private const val PAGE = 700f
