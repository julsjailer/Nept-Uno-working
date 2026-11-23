package com.example.neptuno.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NeptunoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DB_NAME = "neptuno_local.db"

        @Volatile
        private var INSTANCE: NeptunoDatabase? = null

        fun getInstance(context: Context): NeptunoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeptunoDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
