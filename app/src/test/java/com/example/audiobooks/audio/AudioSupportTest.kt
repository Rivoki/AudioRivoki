package com.example.audiobooks.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioSupportTest {
    @Test
    fun supportedExtensionsAreRecognizedCaseInsensitively() {
        assertTrue(AudioSupport.isSupportedAudio(File("track.MP3")))
        assertTrue(AudioSupport.isSupportedAudio(File("track.flac")))
        assertTrue(AudioSupport.isSupportedAudio(File("track.ogg")))
    }

    @Test
    fun unsupportedExtensionsAreRejected() {
        assertFalse(AudioSupport.isSupportedAudio(File("cover.jpg")))
        assertFalse(AudioSupport.isSupportedAudio(File("notes.txt")))
    }
}
