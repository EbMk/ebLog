package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Ensures that when this text field receives focus or when the software keyboard (IME)
 * appears, if the text field is below or obscured, it scrolls up so the field is
 * positioned on top of the keyboard with comfortable breathing room.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocus(
    extraBottomSpace: Dp = 12.dp
): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val extraBottomPx = with(density) { extraBottomSpace.toPx() }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    val imeInsets = WindowInsets.ime
    val isImeVisible = imeInsets.getBottom(density) > 0

    // When keyboard becomes visible while this field is focused, bring into view above keyboard
    LaunchedEffect(isImeVisible, isFocused) {
        if (isFocused && isImeVisible) {
            delay(120)
            val size = layoutCoordinates?.size
            if (size != null && size.height > 0) {
                requester.bringIntoView(
                    androidx.compose.ui.geometry.Rect(
                        0f,
                        0f,
                        size.width.toFloat(),
                        size.height.toFloat() + extraBottomPx
                    )
                )
            } else {
                requester.bringIntoView()
            }
        }
    }

    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { layoutCoordinates = it }
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            if (focusState.isFocused) {
                scope.launch {
                    delay(200)
                    val size = layoutCoordinates?.size
                    if (size != null && size.height > 0) {
                        requester.bringIntoView(
                            androidx.compose.ui.geometry.Rect(
                                0f,
                                0f,
                                size.width.toFloat(),
                                size.height.toFloat() + extraBottomPx
                            )
                        )
                    } else {
                        requester.bringIntoView()
                    }
                }
            }
        }
}

/**
 * Intercepts double-tap gestures to select all text in a text field if the field contains data,
 * and detects long-press gestures to display custom actions (Copy, Paste, Cut, Select All, Clear).
 * Does not consume normal single taps, allowing natural focus, cursor placement, and keyboard opening.
 */
fun Modifier.doubleTapSelectAll(
    text: String,
    focusRequester: FocusRequester? = null,
    onSelectAll: () -> Unit,
    onLongPress: ((Offset) -> Unit)? = null
): Modifier = this.pointerInput(text, onLongPress) {
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTapTime
        val posDiff = (down.position - lastTapPosition).getDistance()

        if (timeDiff in 40..450 && posDiff < 140f) {
            // Double-tap detected
            if (text.isNotEmpty()) {
                focusRequester?.requestFocus()
                onSelectAll()
                down.consume()
            }
            lastTapTime = 0L
        } else {
            lastTapTime = currentTime
            lastTapPosition = down.position

            if (onLongPress != null) {
                // Wait for long press timeout (400ms)
                try {
                    withTimeout(400) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                break
                            }
                            if ((change.position - down.position).getDistance() > 30f) {
                                break
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    // Long press successfully triggered
                    focusRequester?.requestFocus()
                    onLongPress(down.position)
                    down.consume()
                }
            }
        }
    }
}

/**
 * Floating contextual menu shown when long-pressing a text box.
 * Provides Copy, Paste, Cut, Select All, and Clear options based on state.
 */
@Composable
fun TextBoxContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    position: Offset,
    canCopy: Boolean,
    canPaste: Boolean,
    canCut: Boolean,
    canSelectAll: Boolean,
    canClear: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onCut: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    val xOffset = with(density) { (position.x - 100).toInt().coerceAtLeast(10) }
    val yOffset = with(density) { (position.y - 70).toInt().coerceAtLeast(10) }

    Popup(
        offset = IntOffset(xOffset, yOffset),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E2638),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canPaste) {
                        ContextMenuActionItem(
                            icon = Icons.Default.ContentPaste,
                            label = "Paste",
                            onClick = {
                                onPaste()
                                onDismissRequest()
                            }
                        )
                    }
                    if (canCopy) {
                        ContextMenuActionItem(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy",
                            onClick = {
                                onCopy()
                                onDismissRequest()
                            }
                        )
                    }
                    if (canCut) {
                        ContextMenuActionItem(
                            icon = Icons.Default.ContentCut,
                            label = "Cut",
                            onClick = {
                                onCut()
                                onDismissRequest()
                            }
                        )
                    }
                    if (canSelectAll) {
                        ContextMenuActionItem(
                            icon = Icons.Default.SelectAll,
                            label = "Select All",
                            onClick = {
                                onSelectAll()
                                onDismissRequest()
                            }
                        )
                    }
                    if (canClear) {
                        ContextMenuActionItem(
                            icon = Icons.Default.Clear,
                            label = "Clear",
                            onClick = {
                                onClear()
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF93C5FD),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Drop-in OutlinedTextField replacement that selects all text when double tapped on a field with data,
 * and shows contextual menu with Copy, Paste, Cut, Select All, and Clear when long pressed.
 */
@Composable
fun SelectableOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    val focusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }

    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    LaunchedEffect(value) {
        if (textFieldValueState.text != value) {
            textFieldValueState = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    val hasClipboardContent = clipboardManager.hasText()
    val hasText = textFieldValueState.text.isNotEmpty()
    val isSelected = textFieldValueState.selection.length > 0

    Box(modifier = Modifier.wrapContentSize()) {
        OutlinedTextField(
            value = textFieldValueState,
            onValueChange = { newVal ->
                textFieldValueState = newVal
                onValueChange(newVal.text)
            },
            modifier = modifier
                .bringIntoViewOnFocus()
                .focusRequester(focusRequester)
                .doubleTapSelectAll(
                    text = textFieldValueState.text,
                    focusRequester = focusRequester,
                    onSelectAll = {
                        textFieldValueState = textFieldValueState.copy(
                            selection = TextRange(0, textFieldValueState.text.length)
                        )
                    },
                    onLongPress = if (enabled) { pos ->
                        contextMenuPosition = pos
                        showContextMenu = true
                    } else null
                ),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = colors
        )

        TextBoxContextMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            position = contextMenuPosition,
            canCopy = hasText,
            canPaste = !readOnly && hasClipboardContent,
            canCut = !readOnly && (isSelected || hasText),
            canSelectAll = hasText && textFieldValueState.selection.length < textFieldValueState.text.length,
            canClear = !readOnly && hasText,
            onCopy = {
                val selectedText = if (isSelected) {
                    val start = textFieldValueState.selection.min
                    val end = textFieldValueState.selection.max
                    textFieldValueState.text.substring(start, end)
                } else {
                    textFieldValueState.text
                }
                clipboardManager.setText(AnnotatedString(selectedText))
            },
            onPaste = {
                val clipText = clipboardManager.getText()?.text ?: ""
                val selStart = textFieldValueState.selection.min
                val selEnd = textFieldValueState.selection.max
                val currentText = textFieldValueState.text
                val newText = if (selStart != selEnd) {
                    currentText.replaceRange(selStart, selEnd, clipText)
                } else {
                    val cursor = textFieldValueState.selection.start.coerceIn(0, currentText.length)
                    currentText.substring(0, cursor) + clipText + currentText.substring(cursor)
                }
                val newCursor = (if (selStart != selEnd) selStart else textFieldValueState.selection.start) + clipText.length
                textFieldValueState = TextFieldValue(newText, TextRange(newCursor))
                onValueChange(newText)
            },
            onCut = {
                if (isSelected) {
                    val selStart = textFieldValueState.selection.min
                    val selEnd = textFieldValueState.selection.max
                    val selectedText = textFieldValueState.text.substring(selStart, selEnd)
                    clipboardManager.setText(AnnotatedString(selectedText))
                    val newText = textFieldValueState.text.removeRange(selStart, selEnd)
                    textFieldValueState = TextFieldValue(newText, TextRange(selStart))
                    onValueChange(newText)
                } else {
                    clipboardManager.setText(AnnotatedString(textFieldValueState.text))
                    textFieldValueState = TextFieldValue("", TextRange(0))
                    onValueChange("")
                }
            },
            onSelectAll = {
                textFieldValueState = textFieldValueState.copy(
                    selection = TextRange(0, textFieldValueState.text.length)
                )
            },
            onClear = {
                textFieldValueState = TextFieldValue("", TextRange(0))
                onValueChange("")
            }
        )
    }
}
