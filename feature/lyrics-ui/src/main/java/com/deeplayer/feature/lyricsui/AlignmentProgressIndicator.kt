package com.deeplayer.feature.lyricsui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deeplayer.core.contracts.AlignmentProgress

@Composable
fun AlignmentProgressIndicator(progress: AlignmentProgress, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    when (progress) {
      is AlignmentProgress.Processing -> {
        val fraction =
          if (progress.totalChunks > 0) {
            progress.chunkIndex.toFloat() / progress.totalChunks
          } else {
            0f
          }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Processing chunk ${progress.chunkIndex + 1} / ${progress.totalChunks}",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      is AlignmentProgress.PartialResult -> {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Aligned ${progress.lines.size} lines so far...",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      is AlignmentProgress.Complete -> {
        Text(
          text = "Alignment complete",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      is AlignmentProgress.Failed -> {
        Text(
          text = "Alignment failed: ${progress.error.message ?: "Unknown error"}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
        if (progress.retriesLeft > 0) {
          Text(
            text = "Retrying... (${progress.retriesLeft} attempts left)",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun AlignmentProgressIndicatorProcessingPreview() {
  AlignmentProgressIndicator(progress = AlignmentProgress.Processing(2, 7))
}
