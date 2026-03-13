package com.assistant.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.assistant.core.database.dao.*
import com.assistant.core.database.entity.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContextMemoryEntity::class,
        ModelConfigEntity::class,
        DownloadTaskEntity::class,
        PersonaConfigEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun contextMemoryDao(): ContextMemoryDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun personaConfigDao(): PersonaConfigDao

    companion object {
        const val DATABASE_NAME = "assistant.db"
    }
}
