package com.example.audiobooks.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.audiobooks.data.db.entities.BookEntity
import com.example.audiobooks.data.db.entities.ChapterEntity
import com.example.audiobooks.data.db.entities.CollectionEntity
import com.example.audiobooks.data.db.entities.TrackEntity
import com.example.audiobooks.data.db.model.BookWithTracks
import com.example.audiobooks.data.db.model.CollectionWithBooks
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Transaction
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeCollectionsWithBooks(): Flow<List<CollectionWithBooks>>

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collectionEntity: CollectionEntity): Long

    @Query("UPDATE collections SET name = :newName WHERE id = :id")
    suspend fun renameCollection(id: Long, newName: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM collections")
    suspend fun deleteAllCollections()

    @Query("SELECT id FROM books WHERE collectionId = :collectionId")
    suspend fun getBookIdsByCollection(collectionId: Long): List<Long>

    @Query("SELECT id FROM tracks WHERE bookId IN (SELECT id FROM books WHERE collectionId = :collectionId)")
    suspend fun getTrackIdsByCollection(collectionId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(bookEntity: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(trackEntities: List<TrackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapterEntities: List<ChapterEntity>)

    @Transaction
    @Query("SELECT * FROM books WHERE collectionId = :collectionId ORDER BY importedAtEpochMs DESC")
    fun observeBooksByCollection(collectionId: Long): Flow<List<BookWithTracks>>
}
