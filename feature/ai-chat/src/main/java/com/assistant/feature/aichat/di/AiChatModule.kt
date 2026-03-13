package com.assistant.feature.aichat.di

import com.assistant.feature.aichat.data.LlamaEngine
import com.assistant.feature.aichat.data.local.*
import com.assistant.feature.aichat.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiChatModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)    // no timeout for streaming download
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideSendMessageUseCase(
        chatRepository: ChatRepository,
        contextMemoryRepository: ContextMemoryRepository,
        personaRepository: PersonaRepository,
        llamaEngine: LlamaEngine,
    ) = SendMessageUseCase(chatRepository, contextMemoryRepository, personaRepository, llamaEngine)

    @Provides
    @Singleton
    fun provideBuildContextMemoryUseCase(
        chatRepository: ChatRepository,
        contextMemoryRepository: ContextMemoryRepository,
    ) = BuildContextMemoryUseCase(chatRepository, contextMemoryRepository)

    @Provides
    @Singleton
    fun provideDetectDuplicateChatUseCase(
        chatRepository: ChatRepository,
    ) = DetectDuplicateChatUseCase(chatRepository)

    @Provides
    @Singleton
    fun provideChatLifecycleUseCase(
        chatRepository: ChatRepository,
        contextMemoryRepository: ContextMemoryRepository,
    ) = ChatLifecycleUseCase(chatRepository, contextMemoryRepository)
}
