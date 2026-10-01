package com.example.audiobooks.data.repo

import com.example.audiobooks.data.db.AudiobookDao
import com.example.audiobooks.data.db.entities.BookEntity
import com.example.audiobooks.data.db.entities.ChapterEntity
import com.example.audiobooks.data.db.entities.CollectionEntity
import com.example.audiobooks.data.db.entities.TrackEntity
import com.example.audiobooks.data.db.model.BookWithTracks
import com.example.audiobooks.data.db.model.CollectionWithBooks
import com.example.audiobooks.data.model.BookImportData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AudiobookRepository(
    private val dao: AudiobookDao
) {
    data class CollectionCacheKeys(
        val bookIds: List<Long>,
        val trackIds: List<Long>
    )

    fun observeCollectionsWithBooks(): Flow<List<CollectionWithBooks>> = dao.observeCollectionsWithBooks()

    fun observeBooksByCollection(collectionId: Long): Flow<List<BookWithTracks>> =
        dao.observeBooksByCollection(collectionId)

    suspend fun createCollection(name: String): Result<Long> = runCatching {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be empty." }
        dao.insertCollection(CollectionEntity(name = trimmed))
    }

    suspend fun renameCollection(collectionId: Long, newName: String): Result<Unit> = runCatching {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be empty." }
        dao.renameCollection(collectionId, trimmed)
    }

    suspend fun deleteCollection(collectionId: Long): Result<Unit> = runCatching {
        dao.deleteCollection(collectionId)
    }

    suspend fun deleteAllCollections(): Result<Unit> = runCatching {
        dao.deleteAllCollections()
    }

    suspend fun getCollectionName(collectionId: Long): String? {
        return dao.observeCollections().first().firstOrNull { it.id == collectionId }?.name
    }

    suspend fun getCollectionCacheKeys(collectionId: Long): CollectionCacheKeys {
        return CollectionCacheKeys(
            bookIds = dao.getBookIdsByCollection(collectionId),
            trackIds = dao.getTrackIdsByCollection(collectionId)
        )
    }

    suspend fun saveImportedBook(collectionId: Long, importData: BookImportData): Long {
        val bookId = dao.insertBook(
            BookEntity(
                collectionId = collectionId,
                title = importData.title,
                folderPath = importData.folderPath,
                coverArtPath = importData.coverArtPath
            )
        )

        val trackIds = dao.insertTracks(
            importData.tracks.mapIndexed { index, track ->
                TrackEntity(
                    bookId = bookId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    trackNumber = track.trackNumber,
                    durationMs = track.durationMs,
                    filePath = track.filePath,
                    mimeType = track.mimeType,
                    sortOrder = index
                )
            }
        )

        val chapterRows = mutableListOf<ChapterEntity>()
        trackIds.forEachIndexed { trackIndex, trackId ->
            importData.tracks[trackIndex].chapters.forEach { chapter ->
                chapterRows.add(
                    ChapterEntity(
                        trackId = trackId,
                        title = chapter.title,
                        startMs = chapter.startMs,
                        endMs = chapter.endMs
                    )
                )
            }
        }
        if (chapterRows.isNotEmpty()) dao.insertChapters(chapterRows)

        return bookId
    }
}
