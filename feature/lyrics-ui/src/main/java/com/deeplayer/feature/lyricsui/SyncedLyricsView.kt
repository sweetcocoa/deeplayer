package com.deeplayer.feature.lyricsui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment

@Composable
fun SyncedLyricsView(
  lyrics: List<LineAlignment>,
  currentPositionMs: Long,
  globalOffsetMs: Long = 0,
  onLineClick: (lineIndex: Int) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()

  val currentIndex by
    remember(lyrics, currentPositionMs, globalOffsetMs) {
      derivedStateOf {
        LyricsLineHighlighter.currentLineIndex(lyrics, currentPositionMs, globalOffsetMs)
      }
    }

  // Auto-scroll to center the current line
  LaunchedEffect(currentIndex) {
    if (currentIndex >= 0) {
      listState.animateScrollToItem(
        index = currentIndex,
        scrollOffset = -200, // offset to roughly center the item
      )
    }
  }

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(vertical = 120.dp),
    modifier = modifier,
  ) {
    itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
      LyricsLine(
        line = line,
        lineState =
          when {
            index < currentIndex -> LineState.PAST
            index == currentIndex -> LineState.CURRENT
            else -> LineState.UPCOMING
          },
        wordProgress =
          if (index == currentIndex) {
            LyricsLineHighlighter.wordProgress(line, currentPositionMs, globalOffsetMs)
          } else if (index < currentIndex) {
            line.wordAlignments.size.toFloat()
          } else {
            0f
          },
        onClick = { onLineClick(index) },
      )
    }
  }
}

private enum class LineState {
  PAST,
  CURRENT,
  UPCOMING,
}

@Composable
private fun LyricsLine(
  line: LineAlignment,
  lineState: LineState,
  wordProgress: Float,
  onClick: () -> Unit,
) {
  val baseColor by
    animateColorAsState(
      targetValue =
        when (lineState) {
          LineState.PAST -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
          LineState.CURRENT -> MaterialTheme.colorScheme.primary
          LineState.UPCOMING -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
      animationSpec = tween(durationMillis = 200),
      label = "lineColor",
    )

  val fontSize =
    when (lineState) {
      LineState.CURRENT -> 22.sp
      else -> 18.sp
    }

  val fontWeight =
    when (lineState) {
      LineState.CURRENT -> FontWeight.Bold
      else -> FontWeight.Normal
    }

  Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp)) {
    if (lineState == LineState.CURRENT && line.wordAlignments.isNotEmpty()) {
      // Word-level highlight using annotated string
      val highlightColor = MaterialTheme.colorScheme.primary
      val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
      val completedWords = wordProgress.toInt()
      val partialProgress = wordProgress - completedWords

      Text(
        text =
          buildAnnotatedString {
            line.wordAlignments.forEachIndexed { i, word ->
              if (i > 0) append(" ")
              val style =
                when {
                  i < completedWords ->
                    SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)
                  i == completedWords ->
                    SpanStyle(
                      color = lerp(dimColor, highlightColor, partialProgress.coerceIn(0f, 1f)),
                      fontWeight = FontWeight.Bold,
                    )
                  else -> SpanStyle(color = dimColor, fontWeight = FontWeight.Normal)
                }
              withStyle(style) { append(word.word) }
            }
          },
        fontSize = fontSize,
      )
    } else {
      Text(text = line.text, color = baseColor, fontSize = fontSize, fontWeight = fontWeight)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun SyncedLyricsViewPreview() {
  val sampleLyrics =
    listOf(
      LineAlignment(
        text = "First line of lyrics",
        startMs = 0,
        endMs = 3000,
        wordAlignments =
          listOf(
            WordAlignment("First", 0, 800, 0.9f, 0),
            WordAlignment("line", 800, 1400, 0.9f, 0),
            WordAlignment("of", 1400, 1800, 0.9f, 0),
            WordAlignment("lyrics", 1800, 3000, 0.9f, 0),
          ),
      ),
      LineAlignment(
        text = "Second line here",
        startMs = 3000,
        endMs = 6000,
        wordAlignments =
          listOf(
            WordAlignment("Second", 3000, 4000, 0.9f, 1),
            WordAlignment("line", 4000, 5000, 0.9f, 1),
            WordAlignment("here", 5000, 6000, 0.9f, 1),
          ),
      ),
      LineAlignment(
        text = "Third line coming up",
        startMs = 6000,
        endMs = 9000,
        wordAlignments =
          listOf(
            WordAlignment("Third", 6000, 7000, 0.9f, 2),
            WordAlignment("line", 7000, 7500, 0.9f, 2),
            WordAlignment("coming", 7500, 8000, 0.9f, 2),
            WordAlignment("up", 8000, 9000, 0.9f, 2),
          ),
      ),
    )
  SyncedLyricsView(lyrics = sampleLyrics, currentPositionMs = 4500)
}
