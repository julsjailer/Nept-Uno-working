package com.example.neptuno.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NeptunoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DB_NAME = "neptuno_local.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversations ADD COLUMN cloudSyncedAtMillis INTEGER")
                database.execSQL("ALTER TABLE messages ADD COLUMN cloudSyncedAtMillis INTEGER")
                database.execSQL("ALTER TABLE memory_entries ADD COLUMN cloudSyncedAtMillis INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: NeptunoDatabase? = null

        fun getInstance(context: Context): NeptunoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeptunoDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
