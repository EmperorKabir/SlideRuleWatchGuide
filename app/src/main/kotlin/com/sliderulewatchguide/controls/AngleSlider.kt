package com.sliderulewatchguide.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun AngleSlider(
    angle: Double,
    onAngleChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text("Bezel rotation: ${"%.1f".format(angle)}°", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = angle.toFloat(),
            onValueChange = { onAngleChange(it.toDouble()) },
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
