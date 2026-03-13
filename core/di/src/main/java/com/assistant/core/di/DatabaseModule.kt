package com.assistant.core.di

import android.content.Context
import androidx.room.Room
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideContextMemoryDao(db: AppDatabase): ContextMemoryDao = db.contextMemoryDao()
    @Provides fun provideModelConfigDao(db: AppDatabase): ModelConfigDao = db.modelConfigDao()
    @Provides fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()
    @Provides fun providePersonaConfigDao(db: AppDatabase): PersonaConfigDao = db.personaConfigDao()
}
