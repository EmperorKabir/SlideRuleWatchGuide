package com.sliderulewatchguide.equations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val DEBOUNCE_MS = 400L

/**
 * Single labelled tiny text field used by both bezel-input panels (the
 * Outer/Inner pair on the bottom-left and the STAT/NAUT/KM trio on the
 * bottom-right of the watch).
 *
 * Commit triggers (any of):
 *   • 400 ms of typing inactivity after the user last changed the value
 *     (debounce — silent, does NOT hide the keyboard, lets the user
 *     keep editing).
 *   • Focus loss (tab away, tap outside).
 *   • IME action (Enter / Done) — hides the keyboard as before.
 *
 * Programmatic value changes pushed back from the ViewModel (e.g. when
 * the bezel rotates and refreshes the Outer field) do NOT fire the
 * debounce because the debounce counter only advances on user-typed
 * input via the BasicTextField onValueChange callback.
 *
 * While the debounce is counting down, the field border tints to the
 * theme primary colour so the user has a subtle visual cue that a
 * commit is pending — without forcing them to act.
 */
@Composable
fun LabelledFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    imeAction: ImeAction,
    labelWidthDp: Int = 38
) {
    var hadFocus by remember { mutableStateOf(false) }
    var debounceTick by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf(false) }

    LaunchedEffect(debounceTick) {
        if (debounceTick > 0) {
            delay(DEBOUNCE_MS)
            pending = false
            onCommit()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidthDp.dp)
        )
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = if (pending) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .onFocusChanged { focus ->
                    if (focus.isFocused) hadFocus = true
                    else if (hadFocus) {
                        hadFocus = false
                        pending = false
                        onCommit()
                    }
                }
        ) {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() || it == '.' }
                    if (filtered != value) {
                        onValueChange(filtered)
                        pending = true
                        debounceTick++
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(onDone = {
                    pending = false
                    onCommit()
                }),
                modifier = Modifier.width(48.dp).heightIn(min = 16.dp)
            )
        }
    }
}
