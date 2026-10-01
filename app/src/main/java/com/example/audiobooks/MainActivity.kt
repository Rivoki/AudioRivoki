package com.example.audiobooks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobooks.player.PlayerViewModel
import com.example.audiobooks.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        val isDarkMode = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        if (isDarkMode) {
            setTheme(R.style.Theme_AudiobookArchivePlayer_Dark)
        } else {
            setTheme(R.style.Theme_AudiobookArchivePlayer)
        }
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)

        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = AppViewModel.Factory(
                    repository = container.repository,
                    archiveImporter = container.archiveImporter,
                    playerController = container.playerController
                )
            )
            val playerViewModel: PlayerViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return PlayerViewModel(container.playerController) as T
                    }
                }
            )

            AudiobookApp(
                appViewModel = appViewModel,
                playerViewModel = playerViewModel
            )
        }
    }

    override fun onStop() {
        super.onStop()
        container.playerController.persistCurrentPlaybackPosition()
    }
}
