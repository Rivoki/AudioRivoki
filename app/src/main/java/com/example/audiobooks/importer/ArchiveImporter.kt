package com.example.audiobooks.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.audiobooks.audio.AudioSupport
import com.example.audiobooks.data.model.BookImportData
import com.example.audiobooks.metadata.MetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ArchiveImporter(
    private val context: Context,
    private val zipExtractor: ZipExtractor = ZipExtractor(),
    private val rarExtractor: RarExtractor = RarExtractor(),
    private val metadataReader: MetadataReader = MetadataReader()
) {
    private fun appFilesRoot(): File = File(context.filesDir, "collections")

    suspend fun deleteCollectionFolder(collectionName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val collectionDir = File(appFilesRoot(), collectionName)
            if (collectionDir.exists()) {
                collectionDir.deleteRecursively()
            }
        }
    }

    suspend fun clearAllImportedData(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootDir = appFilesRoot()
            if (rootDir.exists()) {
                rootDir.deleteRecursively()
            }
        }
    }

    suspend fun importArchiveToCollection(
        collectionName: String,
        archiveUri: Uri
    ): Result<BookImportData> = withContext(Dispatchers.IO) {
        runCatching {
            val extension = resolveArchiveExtension(archiveUri)
            require(extension in setOf("zip", "rar")) { "Only .zip and .rar are supported." }

            val workDir = File(context.cacheDir, "import_${UUID.randomUUID()}")
            try {
                val archiveCopy = File(workDir, "source.$extension")
                val extractedDir = File(workDir, "extracted")
                workDir.mkdirs()
                extractedDir.mkdirs()

                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    archiveCopy.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open archive stream.")

                when (extension) {
                    "zip" -> zipExtractor.extract(archiveCopy, extractedDir)
                    "rar" -> rarExtractor.extract(archiveCopy, extractedDir)
                }

                val collectionDir = File(appFilesRoot(), collectionName).apply {
                    if (!exists()) {
                        require(mkdirs()) { "Не удалось создать папку ${absolutePath}" }
                    }
                }
                val sourceName = resolveDisplayName(archiveUri)
                val bookFolderName = sourceName
                    ?.substringBeforeLast('.')
                    ?.ifBlank { "Book_${System.currentTimeMillis()}" }
                    ?: "Book_${System.currentTimeMillis()}"
                val bookDir = File(collectionDir, bookFolderName.sanitize()).apply {
                    if (!exists()) {
                        require(mkdirs()) { "Не удалось создать папку ${absolutePath}" }
                    }
                }

                extractedDir.walkTopDown()
                    .filter { it.isFile && AudioSupport.isSupportedAudio(it) }
                    .forEach { source ->
                        val target = File(bookDir, source.name)
                        source.copyTo(target, overwrite = true)
                    }

                val metadata = metadataReader.readBookMetadata(bookDir)
                require(metadata.tracks.isNotEmpty()) { "No supported audio files found in archive." }

                BookImportData(
                    title = bookFolderName,
                    folderPath = bookDir.absolutePath,
                    coverArtPath = metadata.coverArtPath,
                    tracks = metadata.tracks
                )
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun String.sanitize(): String = replace(Regex("""[^a-zA-Z0-9_\-. ]"""), "_")

    private fun resolveArchiveExtension(uri: Uri): String {
        val displayNameExt = resolveDisplayName(uri)
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        if (displayNameExt in setOf("zip", "rar")) return displayNameExt

        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        return when (mime) {
            "application/zip",
            "application/x-zip-compressed" -> "zip"
            "application/vnd.rar",
            "application/x-rar-compressed" -> "rar"
            else -> uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase()
                .orEmpty()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment
    }
}
