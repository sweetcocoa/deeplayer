package com.deeplayer.feature.lyricsui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LyricsEditor(
  initialText: String = "",
  onLyricsSubmit: (List<String>) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember { mutableStateOf(initialText) }

  Column(modifier = modifier.padding(16.dp)) {
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
      // Line numbers
      val lines = text.split("\n")
      val lineNumbers = lines.indices.joinToString("\n") { "${it + 1}" }
      Text(
        text = lineNumbers,
        style =
          TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
          ),
        modifier = Modifier.padding(end = 8.dp),
      )

      Spacer(modifier = Modifier.width(8.dp))

      BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle =
          TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        val lines = text.split("\n").filter { it.isNotBlank() }
        onLyricsSubmit(lines)
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Submit Lyrics")
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun LyricsEditorPreview() {
  LyricsEditor(initialText = "Line one\nLine two\nLine three", onLyricsSubmit = {})
}
