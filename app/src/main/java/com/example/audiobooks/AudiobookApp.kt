package com.example.audiobooks

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audiobooks.player.PlayerViewModel
import com.example.audiobooks.settings.ThemePreferences
import com.example.audiobooks.ui.AppViewModel
import com.example.audiobooks.ui.books.BooksScreen
import com.example.audiobooks.ui.collections.CollectionsScreen
import com.example.audiobooks.ui.player.PlayerScreen
import com.example.audiobooks.ui.player.StickyPlayerControls
import com.example.audiobooks.ui.theme.AudioRivokiTheme

@Composable
fun AudiobookApp(
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val uiState by appViewModel.uiState.collectAsState()
    val lastPlayedState by playerViewModel.lastPlayed.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val nowPlayingState by playerViewModel.nowPlaying.collectAsState()
    val listenedProgress by playerViewModel.listenedProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionsRequested by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val themePreferences = remember(context) { ThemePreferences(context) }
    var isDarkMode by rememberSaveable { mutableStateOf(themePreferences.isDarkMode()) }

    val archivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val selectedCollection = uiState.selectedCollectionId ?: return@rememberLauncherForActivityResult
        if (uri != null) appViewModel.importArchive(selectedCollection, uri)
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* No-op; permission state is checked on next recomposition */ }

    val selectedBook = uiState.books.firstOrNull { it.book.id == uiState.selectedBookId }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            appViewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.selectedBookId) {
        selectedBook?.let { playerViewModel.loadBook(it) }
    }

    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            val neededPermissions = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (neededPermissions.isNotEmpty()) {
                permissionsLauncher.launch(neededPermissions.toTypedArray())
            }
            permissionsRequested = true
        }
    }

    AudioRivokiTheme(isDarkMode = isDarkMode) {
        Scaffold(
            bottomBar = {
                StickyPlayerControls(
                    player = playerViewModel.player,
                    isPlaying = isPlaying,
                    nowPlaying = nowPlayingState,
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onPrev = playerViewModel::seekPrev,
                    onSeekBack10 = playerViewModel::seekBack10Seconds,
                    onSeekForward10 = playerViewModel::seekForward10Seconds,
                    onNext = playerViewModel::seekNext,
                    onSeekTo = playerViewModel::seekTo
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Аудиокнижный плеер от Рината", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { showMenu = true }) {
                        val iconBg = if (isDarkMode) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        Image(
                            painter = painterResource(
                                id = if (isDarkMode) R.drawable.more_icon else R.drawable.more_icon_dark
                            ),
                            contentDescription = "Меню",
                            modifier = Modifier
                                .size(28.dp)
                                .background(iconBg, CircleShape)
                                .padding(4.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Очистить память") },
                            onClick = {
                                showMenu = false
                                showClearCacheDialog = true
                            }
                        )
                    }
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator()
                }

                CollectionsScreen(
                    collections = uiState.collections,
                    selectedCollectionId = uiState.selectedCollectionId,
                    isDarkMode = isDarkMode,
                    onSelectCollection = appViewModel::selectCollection,
                    onCreateCollection = appViewModel::createCollection,
                    onRenameCollection = appViewModel::renameCollection,
                    onDeleteCollection = appViewModel::deleteCollection,
                    onToggleTheme = {
                        isDarkMode = !isDarkMode
                        themePreferences.setDarkMode(isDarkMode)
                    }
                )

                BooksScreen(
                    books = uiState.books,
                    selectedBookId = uiState.selectedBookId,
                    canImport = uiState.selectedCollectionId != null && !uiState.isLoading,
                    onImportClick = { archivePickerLauncher.launch(arrayOf("*/*")) },
                    onBookClick = appViewModel::selectBook
                )

                PlayerScreen(
                    currentBook = selectedBook,
                    lastPlayed = lastPlayedState,
                    nowPlaying = nowPlayingState,
                    listenedProgress = listenedProgress,
                    onTrackSelect = { trackId ->
                        selectedBook?.let { playerViewModel.playTrack(it, trackId) }
                    }
                )
            }

            if (showClearCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearCacheDialog = false },
                    title = { Text("Подтверждение") },
                    text = {
                        Text(
                            "Вы уверены, что хотите очистить кэш?\n" +
                                "Это сотрёт все книги из приложения"
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                appViewModel.clearCache()
                                showClearCacheDialog = false
                            }
                        ) {
                            Text("Да")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.Button(
                            onClick = { showClearCacheDialog = false }
                        ) {
                            Text("Нет")
                        }
                    }
                )
            }
        }
    }
}
