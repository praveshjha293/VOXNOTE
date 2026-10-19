package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RecordingEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NotesMakerDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NotesMakerDatabase? = null

        fun getInstance(context: Context): NotesMakerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesMakerDatabase::class.java,
                    "notes_maker.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
