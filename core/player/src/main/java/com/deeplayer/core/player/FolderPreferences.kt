package com.deeplayer.core.player

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderPreferences @Inject constructor(private val prefs: SharedPreferences) {

  fun getSelectedFolders(): Set<String> {
    return prefs.getStringSet(KEY_SELECTED_FOLDERS, emptySet()) ?: emptySet()
  }

  fun setSelectedFolders(folders: Set<String>) {
    prefs.edit().putStringSet(KEY_SELECTED_FOLDERS, folders).apply()
  }

  fun addFolder(path: String) {
    val current = getSelectedFolders().toMutableSet()
    current.add(path)
    setSelectedFolders(current)
  }

  fun removeFolder(path: String) {
    val current = getSelectedFolders().toMutableSet()
    current.remove(path)
    setSelectedFolders(current)
  }

  companion object {
    private const val KEY_SELECTED_FOLDERS = "selected_folders"
  }
}
