package com.example.audiobooks.metadata

import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import com.example.audiobooks.audio.AudioSupport
import com.example.audiobooks.data.model.ChapterInfo
import com.example.audiobooks.data.model.TrackImportData
import java.io.File

class MetadataReader(
    private val chapterParser: ChapterParser = ChapterParser()
) {
    data class BookMetadataResult(
        val tracks: List<TrackImportData>,
        val coverArtPath: String?
    )

    fun readBookMetadata(bookDir: File): BookMetadataResult {
        val audioFiles = bookDir.walkTopDown()
            .filter { it.isFile && AudioSupport.isSupportedAudio(it) }
            .sortedBy { it.name.lowercase() }
            .toList()

        val tracks = audioFiles.mapIndexed { index, file ->
            readTrack(file, index)
        }

        val coverPath = extractCoverArt(audioFiles.firstOrNull(), bookDir)
        return BookMetadataResult(tracks = tracks, coverArtPath = coverPath)
    }

    private fun readTrack(file: File, index: Int): TrackImportData {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore("/")
                ?.toIntOrNull()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val chapters: List<ChapterInfo> = chapterParser.parseChapters(file)

            TrackImportData(
                title = title,
                artist = artist,
                album = album,
                trackNumber = trackNumber ?: (index + 1),
                durationMs = duration,
                filePath = file.absolutePath,
                mimeType = file.toMimeType(),
                chapters = chapters
            )
        } catch (_: Exception) {
            TrackImportData(
                title = file.nameWithoutExtension,
                artist = null,
                album = null,
                trackNumber = index + 1,
                durationMs = null,
                filePath = file.absolutePath,
                mimeType = file.toMimeType(),
                chapters = emptyList()
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractCoverArt(firstAudioFile: File?, bookDir: File): String? {
        if (firstAudioFile == null) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(firstAudioFile.absolutePath)
            val bytes = retriever.embeddedPicture ?: return null
            val output = File(bookDir, "cover.jpg")
            output.writeBytes(bytes)
            output.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun File.toMimeType(): String? {
        val ext = extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "flac" -> "audio/flac"
                "ogg" -> "audio/ogg"
                else -> null
            }
    }
}
