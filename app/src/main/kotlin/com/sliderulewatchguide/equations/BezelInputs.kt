package com.sliderulewatchguide.equations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Outer / Inner pair on the bottom-LEFT corner gap of the watch. No
 * surface fill or container padding around the rows: each labelled field
 * already has its own outlined border, so the surrounding rectangle was
 * just adding visual weight (and overlap on smaller screens). The rows
 * float over whatever sits behind them.
 */
@Composable
fun BezelInputs(
    outer: String,
    inner: String,
    onOuterChange: (String) -> Unit,
    onInnerChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        LabelledFieldRow(
            label = "Outer",
            value = outer,
            onValueChange = onOuterChange,
            onCommit = onCommit,
            imeAction = ImeAction.Next
        )
        LabelledFieldRow(
            label = "Inner",
            value = inner,
            onValueChange = onInnerChange,
            onCommit = onCommit,
            imeAction = ImeAction.Done
        )
    }
}
