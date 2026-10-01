package com.example.audiobooks.data.db.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.audiobooks.data.db.entities.BookEntity
import com.example.audiobooks.data.db.entities.ChapterEntity
import com.example.audiobooks.data.db.entities.CollectionEntity
import com.example.audiobooks.data.db.entities.TrackEntity

data class BookWithTracks(
    @Embedded val book: BookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId",
        entity = TrackEntity::class
    )
    val tracks: List<TrackWithChapters>
)

data class TrackWithChapters(
    @Embedded val track: TrackEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "trackId"
    )
    val chapters: List<ChapterEntity>
)

data class CollectionWithBooks(
    @Embedded val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "collectionId"
    )
    val books: List<BookEntity>
)
