package com.groovebox.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, LinkedFolderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class GrooveBoxDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun linkedFolderDao(): LinkedFolderDao

    companion object {
        @Volatile private var instance: GrooveBoxDatabase? = null

        // Adds custom playlist cover art + folder-backed ("virtual") playlists.
        // Additive-only (nullable columns, no data loss) so existing installs keep
        // their playlists/likes/library instead of a destructive wipe-and-recreate.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverArtPath TEXT")
                db.execSQL("ALTER TABLE playlists ADD COLUMN folderUri TEXT")
            }
        }

        fun get(context: Context): GrooveBoxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GrooveBoxDatabase::class.java,
                    "groovebox.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
