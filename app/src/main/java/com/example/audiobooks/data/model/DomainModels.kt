package com.example.audiobooks.data.model

data class ChapterInfo(
    val title: String,
    val startMs: Long,
    val endMs: Long?
)

data class TrackImportData(
    val title: String,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val filePath: String,
    val mimeType: String?,
    val chapters: List<ChapterInfo>
)

data class BookImportData(
    val title: String,
    val folderPath: String,
    val coverArtPath: String?,
    val tracks: List<TrackImportData>
)
