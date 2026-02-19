package com.deeplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
  availableFolders: List<String> = emptyList(),
  selectedFolders: Set<String> = emptySet(),
  onUpdateSelectedFolders: (Set<String>) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var gaplessPlayback by remember { mutableStateOf(false) }
  var autoLyricsAlignment by remember { mutableStateOf(true) }
  var lyricsFontSize by remember { mutableFloatStateOf(16f) }
  val themeOptions = listOf("시스템", "라이트", "다크")
  var selectedTheme by remember { mutableStateOf("시스템") }
  var showFolderDialog by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(text = "설정", style = MaterialTheme.typography.headlineSmall)

    // 재생
    SettingSection(title = "재생") {
      SettingToggle(
        label = "갭리스 재생",
        checked = gaplessPlayback,
        onCheckedChange = { gaplessPlayback = it },
      )
    }

    // 라이브러리
    SettingSection(title = "라이브러리") {
      SettingClickable(label = "라이브러리 다시 스캔", onClick = { /* no-op for now */ })
      SettingClickable(
        label = "스캔 폴더 관리",
        value = if (selectedFolders.isEmpty()) "전체" else "${selectedFolders.size}개 폴더",
        onClick = { showFolderDialog = true },
      )
    }

    // 가사
    SettingSection(title = "가사") {
      SettingToggle(
        label = "자동 가사 정렬",
        checked = autoLyricsAlignment,
        onCheckedChange = { autoLyricsAlignment = it },
      )
      SettingSlider(
        label = "가사 글꼴 크기",
        value = lyricsFontSize,
        onValueChange = { lyricsFontSize = it },
        valueRange = 12f..28f,
        displayValue = "${lyricsFontSize.toInt()}sp",
      )
    }

    // 테마
    SettingSection(title = "테마") {
      SettingClickable(
        label = "다크/라이트/시스템",
        value = selectedTheme,
        onClick = {
          val currentIndex = themeOptions.indexOf(selectedTheme)
          selectedTheme = themeOptions[(currentIndex + 1) % themeOptions.size]
        },
      )
    }

    // 정보
    SettingSection(title = "정보") {
      SettingClickable(label = "버전", value = "v0.1.0", onClick = {})
      SettingClickable(label = "오픈소스 라이선스", onClick = { /* no-op for now */ })
    }
  }

  if (showFolderDialog) {
    FolderSelectionDialog(
      availableFolders = availableFolders,
      selectedFolders = selectedFolders,
      onConfirm = { folders ->
        onUpdateSelectedFolders(folders)
        showFolderDialog = false
      },
      onDismiss = { showFolderDialog = false },
    )
  }
}

@Composable
private fun FolderSelectionDialog(
  availableFolders: List<String>,
  selectedFolders: Set<String>,
  onConfirm: (Set<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var currentSelection by remember { mutableStateOf(selectedFolders) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("스캔 폴더 관리") },
    text = {
      Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          TextButton(onClick = { currentSelection = availableFolders.toSet() }) { Text("전체 선택") }
          TextButton(onClick = { currentSelection = emptySet() }) { Text("선택 해제") }
        }
        if (availableFolders.isEmpty()) {
          Text(
            text = "사용 가능한 폴더가 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
          )
        } else {
          LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            items(availableFolders) { folder ->
              val isSelected = currentSelection.contains(folder)
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      currentSelection =
                        if (isSelected) {
                          currentSelection - folder
                        } else {
                          currentSelection + folder
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = isSelected,
                  onCheckedChange = { checked ->
                    currentSelection =
                      if (checked) {
                        currentSelection + folder
                      } else {
                        currentSelection - folder
                      }
                  },
                )
                Text(
                  text = folder,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(start = 8.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onConfirm(currentSelection) }) { Text("확인") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
  )
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    HorizontalDivider()
    content()
  }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun SettingClickable(label: String, value: String? = null, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    if (value != null) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SettingSlider(
  label: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  displayValue: String,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = displayValue,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
