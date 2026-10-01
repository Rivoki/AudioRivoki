package com.example.audiobooks.audio

import java.io.File

object AudioSupport {
    val supportedAudioExtensions: Set<String> = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac")

    fun isSupportedAudio(file: File): Boolean {
        return file.extension.lowercase() in supportedAudioExtensions
    }
}
