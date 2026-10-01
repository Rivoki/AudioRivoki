package com.example.audiobooks

import android.content.Context
import com.example.audiobooks.data.db.AppDatabase
import com.example.audiobooks.data.repo.AudiobookRepository
import com.example.audiobooks.importer.ArchiveImporter
import com.example.audiobooks.player.PlayerController

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    val repository = AudiobookRepository(database.audiobookDao())
    val archiveImporter = ArchiveImporter(context)
    val playerController = PlayerController(context)
}
