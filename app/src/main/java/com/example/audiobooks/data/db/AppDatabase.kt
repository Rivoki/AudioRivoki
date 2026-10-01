package com.example.audiobooks.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.audiobooks.data.db.entities.BookEntity
import com.example.audiobooks.data.db.entities.ChapterEntity
import com.example.audiobooks.data.db.entities.CollectionEntity
import com.example.audiobooks.data.db.entities.TrackEntity

@Database(
    entities = [
        CollectionEntity::class,
        BookEntity::class,
        TrackEntity::class,
        ChapterEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audiobookDao(): AudiobookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audiobooks.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
