package com.deeplayer.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deeplayer.core.contracts.TrackMetadata
import kotlinx.coroutines.launch

private val KOREAN_INITIALS =
  listOf('ㄱ', 'ㄴ', 'ㄷ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅅ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')

private val ALPHA_LABELS = ('A'..'Z').map { it }

private val INDEX_LABELS: List<Char> = KOREAN_INITIALS + ALPHA_LABELS + '#'

/**
 * Extracts the scroll index character for a given string. For Korean Hangul syllables, returns the
 * initial consonant (jamo). For ASCII letters, returns the uppercase letter. Otherwise returns '#'.
 */
internal fun indexCharFor(text: String): Char {
  if (text.isBlank()) return '#'
  val first = text.first()
  // Hangul syllable range: AC00-D7A3
  if (first in '\uAC00'..'\uD7A3') {
    val index = (first.code - 0xAC00) / 588
    return if (index in KOREAN_INITIALS.indices) KOREAN_INITIALS[index] else '#'
  }
  if (first.isLetter()) {
    return first.uppercaseChar()
  }
  return '#'
}

/**
 * Builds a map from each index label to the first item position in [tracks] that starts with that
 * label. [textSelector] picks which field to index on (e.g. title or artist).
 */
internal fun buildIndexMap(
  tracks: List<TrackMetadata>,
  textSelector: (TrackMetadata) -> String,
): Map<Char, Int> {
  val map = mutableMapOf<Char, Int>()
  tracks.forEachIndexed { index, track ->
    val ch = indexCharFor(textSelector(track))
    if (ch !in map) {
      map[ch] = index
    }
  }
  return map
}

/**
 * A vertical fast-scroll bar that appears on the right edge of the track list. Tapping or dragging
 * on a label scrolls the LazyColumn to the first track matching that index character. A large popup
 * indicator is shown while the user is actively dragging.
 */
@Composable
fun FastScrollBar(
  listState: LazyListState,
  indexMap: Map<Char, Int>,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  var isDragging by remember { mutableStateOf(false) }
  var activeLabel by remember { mutableStateOf<Char?>(null) }
  var barHeightPx by remember { mutableStateOf(0f) }

  fun scrollToLabel(yPx: Float) {
    if (barHeightPx <= 0f) return
    val fraction = (yPx / barHeightPx).coerceIn(0f, 1f)
    val labelIndex =
      (fraction * (INDEX_LABELS.size - 1)).toInt().coerceIn(0, INDEX_LABELS.lastIndex)
    val label = INDEX_LABELS[labelIndex]
    activeLabel = label
    val targetIndex = indexMap[label] ?: return
    scope.launch { listState.animateScrollToItem(targetIndex) }
  }

  Box(modifier = modifier.fillMaxHeight()) {
    // Popup indicator
    AnimatedVisibility(
      visible = isDragging && activeLabel != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier =
        Modifier.align(Alignment.CenterEnd).offset {
          IntOffset(x = with(density) { (-48).dp.roundToPx() }, y = 0)
        },
    ) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = activeLabel?.toString() ?: "",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }

    // Index label column
    Box(
      modifier =
        Modifier.width(24.dp)
          .fillMaxHeight()
          .align(Alignment.CenterEnd)
          .onSizeChanged { barHeightPx = it.height.toFloat() }
          .pointerInput(indexMap) { detectTapGestures { offset -> scrollToLabel(offset.y) } }
          .pointerInput(indexMap) {
            detectDragGestures(
              onDragStart = { offset ->
                isDragging = true
                scrollToLabel(offset.y)
              },
              onDragEnd = { isDragging = false },
              onDragCancel = { isDragging = false },
              onDrag = { change, _ ->
                change.consume()
                scrollToLabel(change.position.y)
              },
            )
          }
          .padding(vertical = 4.dp),
      contentAlignment = Alignment.Center,
    ) {
      // Distribute labels evenly across the bar height
      Box(modifier = Modifier.fillMaxHeight()) {
        INDEX_LABELS.forEachIndexed { i, label ->
          val fraction = if (INDEX_LABELS.size <= 1) 0.5f else i.toFloat() / (INDEX_LABELS.size - 1)
          Text(
            text = label.toString(),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            color =
              MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (label in indexMap) 0.8f else 0.35f
              ),
            modifier =
              Modifier.align(Alignment.TopCenter).offset {
                val totalHeight =
                  barHeightPx - with(density) { 8.dp.toPx() } // account for vertical padding
                IntOffset(x = 0, y = (fraction * totalHeight).toInt())
              },
          )
        }
      }
    }
  }
}
