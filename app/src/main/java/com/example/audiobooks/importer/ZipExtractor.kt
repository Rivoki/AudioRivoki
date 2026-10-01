package com.example.audiobooks.importer

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ZipExtractor {
    fun extract(archiveFile: File, destinationDir: File) {
        ZipArchiveInputStream(FileInputStream(archiveFile)).use { input ->
            var entry = input.nextEntry as ZipArchiveEntry?
            while (entry != null) {
                val target = File(destinationDir, entry.name.sanitizeZipPath())
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                entry = input.nextEntry as ZipArchiveEntry?
            }
        }
    }

    private fun String.sanitizeZipPath(): String {
        return replace('\\', '/').trimStart('/').replace("../", "")
    }
}
