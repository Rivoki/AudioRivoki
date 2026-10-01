package com.example.audiobooks.metadata

import android.media.MediaMetadataRetriever
import com.example.audiobooks.data.model.ChapterInfo
import java.io.File

class ChapterParser {
    fun parseChapters(file: File): List<ChapterInfo> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val chapterTag = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            if (!chapterTag.isNullOrBlank()) {
                listOf(ChapterInfo(title = "Chapter $chapterTag", startMs = 0L, endMs = null))
            } else chapterFromFilename(file)
        } catch (_: Exception) {
            chapterFromFilename(file)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun chapterFromFilename(file: File): List<ChapterInfo> {
        val chapterNo = Regex("""(?i)(chapter|ch)[\s._-]*(\d{1,3})""")
            .find(file.nameWithoutExtension)
            ?.groupValues
            ?.getOrNull(2)
        return if (chapterNo != null) {
            listOf(ChapterInfo(title = "Chapter $chapterNo", startMs = 0L, endMs = null))
        } else {
            emptyList()
        }
    }
}
