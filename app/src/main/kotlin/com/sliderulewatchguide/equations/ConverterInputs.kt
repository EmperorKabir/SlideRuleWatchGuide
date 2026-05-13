package com.sliderulewatchguide.equations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * STAT mi / NAUT mi / KM converter trio on the bottom-RIGHT corner gap of
 * the watch. Each field commits independently: the bezel snaps so the
 * typed value sits above the corresponding marker (STAT, NAUT, KM), and
 * the other two fields refresh from the new rotation. No surface fill or
 * container padding: rows float on their own outlined-border fields.
 */
@Composable
fun ConverterInputs(
    stat: String,
    naut: String,
    km: String,
    onStatChange: (String) -> Unit,
    onNautChange: (String) -> Unit,
    onKmChange: (String) -> Unit,
    onCommitStat: () -> Unit,
    onCommitNaut: () -> Unit,
    onCommitKm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // "Input" header aligned to the left edge of the numerical text
        // field. The converter inputs use labelWidthDp = 44, so the field
        // starts 44 dp from the row's left edge.
        Text(
            text = "Input",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 44.dp)
        )
        LabelledFieldRow(
            label = "Stat mi",
            value = stat,
            onValueChange = onStatChange,
            onCommit = onCommitStat,
            imeAction = ImeAction.Next,
            labelWidthDp = 44
        )
        LabelledFieldRow(
            label = "Naut mi",
            value = naut,
            onValueChange = onNautChange,
            onCommit = onCommitNaut,
            imeAction = ImeAction.Next,
            labelWidthDp = 44
        )
        LabelledFieldRow(
            label = "KM",
            value = km,
            onValueChange = onKmChange,
            onCommit = onCommitKm,
            imeAction = ImeAction.Done,
            labelWidthDp = 44
        )
    }
}
