package com.deeplayer.feature.lyricsui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Global offset slider allowing the user to adjust lyrics timing by +/-5 seconds. Calls
 * [onOffsetChange] with the offset in milliseconds as the slider moves.
 */
@Composable
fun OffsetAdjuster(
  currentOffsetMs: Long = 0,
  onOffsetChange: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var sliderValue by remember(currentOffsetMs) { mutableFloatStateOf(currentOffsetMs.toFloat()) }

  Column(
    modifier = modifier.fillMaxWidth().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "Offset: ${formatOffset(sliderValue.toLong())}",
      style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
      value = sliderValue,
      onValueChange = {
        sliderValue = it
        onOffsetChange(it.toLong())
      },
      valueRange = -5000f..5000f,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

private fun formatOffset(ms: Long): String {
  val sign = if (ms >= 0) "+" else "-"
  val abs = kotlin.math.abs(ms)
  val sec = abs / 1000
  val frac = abs % 1000
  return "%s%d.%02ds".format(sign, sec, frac / 10)
}

@Preview(showBackground = true)
@Composable
private fun OffsetAdjusterPreview() {
  OffsetAdjuster(currentOffsetMs = 500, onOffsetChange = {})
}
