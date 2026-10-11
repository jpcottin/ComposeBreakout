package com.jpcottin.composebreakout.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.border
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Compose Styles API definitions. Interaction states (focused, hovered) are
 * declared here instead of being tracked by hand with onFocusChanged in each
 * composable.
 */
object BreakoutStyles {
    /**
     * Invisible until the element is focused, then the border animates to the
     * paddle's cyan. A mouse hover shows a fainter ring. The 3dp border is
     * always there (transparent at rest), so focus never shifts the layout.
     * On a TV, where focus is the only cursor, this is how the gear and the
     * settings rows show where the D-pad is.
     */
    val focusRing: Style = Style {
        border(3.dp, Color.Transparent)
        hovered { borderColor(PaddleCyan.copy(alpha = 0.5f)) }
        focused { animate { borderColor(PaddleCyan) } }
    }
}

/**
 * Attaches [BreakoutStyles.focusRing] to a Material component, driven by the
 * same [MutableInteractionSource] the component reports its focus and hover
 * interactions to.
 */
@Composable
fun Modifier.focusRing(interactionSource: MutableInteractionSource, shape: Shape): Modifier {
    val styleState = rememberUpdatedStyleState(interactionSource) {}
    val shapeStyle = remember(shape) { Style { shape(shape) } }
    return styleable(styleState, BreakoutStyles.focusRing, shapeStyle)
}
