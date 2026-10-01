package com.example.audiobooks.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audiobooks.data.db.model.BookWithTracks
import com.example.audiobooks.data.db.model.CollectionWithBooks
import com.example.audiobooks.data.repo.AudiobookRepository
import com.example.audiobooks.importer.ArchiveImporter
import com.example.audiobooks.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AppUiState(
    val collections: List<CollectionWithBooks> = emptyList(),
    val selectedCollectionId: Long? = null,
    val books: List<BookWithTracks> = emptyList(),
    val selectedBookId: Long? = null,
    val isLoading: Boolean = false,
    val message: String? = null
)

class AppViewModel(
    private val repository: AudiobookRepository,
    private val archiveImporter: ArchiveImporter,
    private val playerController: PlayerController
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var booksObserverJob: Job? = null

    init {
        observeCollections()
    }

    private fun observeCollections() {
        viewModelScope.launch {
            repository.observeCollectionsWithBooks()
                .catch { throwable ->
                    _uiState.update { it.copy(message = throwable.message ?: "Failed to load collections.") }
                }
                .collect { collections ->
                    val currentSelection = _uiState.value.selectedCollectionId
                        ?: collections.firstOrNull()?.collection?.id
                    _uiState.update { it.copy(collections = collections, selectedCollectionId = currentSelection) }
                    currentSelection?.let { observeBooks(it) }
                }
        }
    }

    private fun observeBooks(collectionId: Long) {
        booksObserverJob?.cancel()
        booksObserverJob = viewModelScope.launch {
            repository.observeBooksByCollection(collectionId)
                .catch { throwable ->
                    _uiState.update { it.copy(message = throwable.message ?: "Failed to load books.") }
                }
                .collect { books ->
                    _uiState.update { state ->
                        val resumedBookId = playerController.getResumeForCollection(collectionId)?.bookId
                        val autoSelectedBookId = resumedBookId
                            ?.takeIf { id -> books.any { it.book.id == id } }
                            ?: books.firstOrNull()?.book?.id
                        state.copy(
                            books = books,
                            selectedBookId = state.selectedBookId ?: autoSelectedBookId
                        )
                    }
                }
        }
    }

    fun selectCollection(collectionId: Long) {
        _uiState.update { it.copy(selectedCollectionId = collectionId, selectedBookId = null) }
        observeBooks(collectionId)
    }

    fun selectBook(bookId: Long) {
        _uiState.update { it.copy(selectedBookId = bookId) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            repository.createCollection(name).onFailure { throwable ->
                _uiState.update { it.copy(message = throwable.message ?: "Unable to create collection.") }
            }
        }
    }

    fun renameCollection(collectionId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameCollection(collectionId, newName).onFailure { throwable ->
                _uiState.update { it.copy(message = throwable.message ?: "Unable to rename collection.") }
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val collectionName = repository.getCollectionName(collectionId)
            if (collectionName.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, message = "Папка не найдена.") }
                return@launch
            }
            val cacheKeys = repository.getCollectionCacheKeys(collectionId)

            val deleteDbResult = repository.deleteCollection(collectionId)
            if (deleteDbResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = deleteDbResult.exceptionOrNull()?.message ?: "Не удалось удалить папку."
                    )
                }
                return@launch
            }

            archiveImporter.deleteCollectionFolder(collectionName)
            playerController.clearPlaybackCacheForCollection(
                collectionId = collectionId,
                bookIds = cacheKeys.bookIds,
                trackIds = cacheKeys.trackIds
            )
            _uiState.update { it.copy(isLoading = false, message = "Папка удалена.") }
        }
    }

    fun importArchive(collectionId: Long, archiveUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val collectionName = repository.getCollectionName(collectionId)
            if (collectionName.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, message = "Collection no longer exists.") }
                return@launch
            }
            archiveImporter.importArchiveToCollection(collectionName, archiveUri)
                .onSuccess { data ->
                    repository.saveImportedBook(collectionId, data)
                    _uiState.update { it.copy(isLoading = false, message = "Import complete.") }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, message = throwable.message ?: "Import failed.") }
                }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val dbResult = repository.deleteAllCollections()
            if (dbResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = dbResult.exceptionOrNull()?.message ?: "Не удалось очистить память."
                    )
                }
                return@launch
            }
            archiveImporter.clearAllImportedData()
            playerController.clearAllPlaybackCache()
            _uiState.update { it.copy(isLoading = false, message = "Память очищена.") }
        }
    }

    class Factory(
        private val repository: AudiobookRepository,
        private val archiveImporter: ArchiveImporter,
        private val playerController: PlayerController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(repository, archiveImporter, playerController) as T
        }
    }
}
