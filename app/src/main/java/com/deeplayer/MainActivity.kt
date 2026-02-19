package com.deeplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.deeplayer.ui.MainViewModel
import com.deeplayer.ui.navigation.DeeplaterApp
import com.deeplayer.ui.theme.DeeplaterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { DeeplaterTheme { DeeplaterApp(viewModel = viewModel) } }
  }
}
