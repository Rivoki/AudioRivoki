package com.example.audiobooks.importer

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.FileOutputStream

class RarExtractor {
    fun extract(archiveFile: File, destinationDir: File) {
        Archive(archiveFile).use { archive ->
            var fileHeader: FileHeader? = archive.nextFileHeader()
            while (fileHeader != null) {
                val name = if (fileHeader.isUnicode) {
                    fileHeader.fileNameW
                } else {
                    fileHeader.fileNameString
                }
                val target = File(destinationDir, name.sanitizePath())
                if (fileHeader.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        archive.extractFile(fileHeader, output)
                    }
                }
                fileHeader = archive.nextFileHeader()
            }
        }
    }

    private fun String.sanitizePath(): String {
        return replace('\\', '/').trimStart('/').replace("../", "")
    }
}
