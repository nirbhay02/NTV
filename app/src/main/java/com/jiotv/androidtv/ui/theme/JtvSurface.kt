package com.jiotv.androidtv.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape

private var lastHoverFocusAt = androidx.compose.ui.geometry.Offset.Unspecified

/**
 * Hovering an item focuses it on air mice / TV pointers, but only when pointer itself moved.
 */
fun Modifier.mouseHoverToFocus(focusRequester: FocusRequester): Modifier =
    focusRequester(focusRequester) then HoverFocusElement(focusRequester)

private data class HoverFocusElement(val fr: FocusRequester) : ModifierNodeElement<HoverFocusNode>() {
    override fun create() = HoverFocusNode(fr)
    override fun update(node: HoverFocusNode) { node.fr = fr }
}

private class HoverFocusNode(var fr: FocusRequester) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main || pointerEvent.type != PointerEventType.Enter) return
        val at = requireLayoutCoordinates().localToWindow(pointerEvent.changes.first().position)
        val last = lastHoverFocusAt
        if (last.isSpecified && (at - last).getDistance() < 2f) return
        lastHoverFocusAt = at
        fr.requestFocus()
    }
    override fun onCancelPointerInput() {}
}

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val swallowOkUp = remember { androidx.compose.runtime.mutableStateOf(false) }
    val longClick: (() -> Unit)? = onLongClick?.let { { swallowOkUp.value = true; currentOnLongClick?.invoke() } }

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && swallowOkUp.value) {
                    if (e.type == KeyEventType.KeyUp) swallowOkUp.value = false
                    true
                } else false
            }
            .mouseHoverToFocus(focusRequester)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        coroutineScope.launch {
                            interactionSource.emit(press)
                        }
                        val success = tryAwaitRelease()
                        coroutineScope.launch {
                            if (success) {
                                interactionSource.emit(PressInteraction.Release(press))
                            } else {
                                interactionSource.emit(PressInteraction.Cancel(press))
                            }
                        }
                    },
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick?.invoke() }
                )
            },
        onLongClick = longClick,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = interactionSource,
        content = content
    )
}
