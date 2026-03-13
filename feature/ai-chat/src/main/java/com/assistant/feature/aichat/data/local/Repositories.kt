package com.assistant.feature.aichat.data.local

import com.assistant.core.database.dao.*
import com.assistant.core.database.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── CHAT REPOSITORY ──────────────────────────────────────────────────────────

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()
    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.observeMessages(chatId)

    suspend fun getConversation(id: String) = conversationDao.getById(id)
    suspend fun getMessages(chatId: String) = messageDao.getMessages(chatId)
    suspend fun getRecentMessages(chatId: String, limit: Int) = messageDao.getRecentMessages(chatId, limit)

    suspend fun createConversation(title: String, modelPath: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conversationDao.insert(
            ConversationEntity(
                id = id,
                title = title,
                modelPath = modelPath,
                createdAt = now,
                lastActiveAt = now,
            )
        )
        return id
    }

    suspend fun saveMessage(chatId: String, role: String, content: String, isPartial: Boolean = false): String {
        val id = UUID.randomUUID().toString()
        messageDao.insert(
            MessageEntity(
                id = id,
                conversationId = chatId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis(),
                isPartial = isPartial,
            )
        )
        conversationDao.touchConversation(chatId, System.currentTimeMillis())
        return id
    }

    suspend fun updateMessage(id: String, content: String, isPartial: Boolean) {
        val existing = messageDao.getMessages("").firstOrNull { it.id == id } ?: return
        messageDao.update(existing.copy(content = content, isPartial = isPartial))
    }

    suspend fun pinConversation(id: String, pinned: Boolean, order: Int) =
        conversationDao.setPinned(id, pinned, order)

    suspend fun getPinnedCount() = conversationDao.pinnedCount()
    suspend fun renameConversation(id: String, title: String) = conversationDao.rename(id, title)
    suspend fun deleteConversation(id: String) = conversationDao.deleteById(id)

    suspend fun getExpiredConversations(cutoffMs: Long) = conversationDao.getExpiredConversations(cutoffMs)
    suspend fun markExpiryNotified(id: String) = conversationDao.markExpiryNotified(id)

    suspend fun searchByTitle(query: String) = conversationDao.searchByTitle("%$query%")
}

// ─── MODEL REPOSITORY ─────────────────────────────────────────────────────────

@Singleton
class ModelRepository @Inject constructor(
    private val modelConfigDao: ModelConfigDao,
) {
    fun observeAll(): Flow<List<ModelConfigEntity>> = modelConfigDao.observeAll()
    fun observeActiveModel(): Flow<ModelConfigEntity?> = modelConfigDao.observeActiveModel()

    suspend fun getActiveModel() = modelConfigDao.getActiveModel()
    suspend fun getByPath(path: String) = modelConfigDao.getByPath(path)

    suspend fun saveModel(config: ModelConfigEntity) = modelConfigDao.insert(config)

    suspend fun setActive(path: String) {
        modelConfigDao.clearActive()
        modelConfigDao.setActive(path)
    }

    suspend fun update(config: ModelConfigEntity) = modelConfigDao.update(config)
    suspend fun delete(path: String) = modelConfigDao.delete(path)
}

// ─── DOWNLOAD REPOSITORY ──────────────────────────────────────────────────────

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadTaskDao: DownloadTaskDao,
) {
    fun observeAll(): Flow<List<DownloadTaskEntity>> = downloadTaskDao.observeAll()

    suspend fun getById(id: String) = downloadTaskDao.getById(id)
    suspend fun getActiveDownloads() = downloadTaskDao.getActiveDownloads()

    suspend fun createTask(url: String, destPath: String, displayName: String): String {
        val id = UUID.randomUUID().toString()
        downloadTaskDao.insert(
            DownloadTaskEntity(
                id = id,
                url = url,
                destPath = destPath,
                displayName = displayName,
                status = "QUEUED",
            )
        )
        return id
    }

    suspend fun updateProgress(id: String, bytes: Long, status: String) =
        downloadTaskDao.updateProgress(id, bytes, status)

    suspend fun setWorkerId(id: String, workerId: String) {
        val task = downloadTaskDao.getById(id) ?: return
        downloadTaskDao.update(task.copy(workerId = workerId))
    }

    suspend fun delete(id: String) = downloadTaskDao.delete(id)
}

// ─── CONTEXT MEMORY REPOSITORY ────────────────────────────────────────────────

@Singleton
class ContextMemoryRepository @Inject constructor(
    private val contextMemoryDao: ContextMemoryDao,
) {
    suspend fun get(conversationId: String) = contextMemoryDao.get(conversationId)

    suspend fun upsert(conversationId: String, excerpt: String, tokenCount: Int) =
        contextMemoryDao.upsert(
            ContextMemoryEntity(
                conversationId = conversationId,
                excerpt = excerpt,
                tokenCount = tokenCount,
                updatedAt = System.currentTimeMillis(),
            )
        )

    suspend fun delete(conversationId: String) = contextMemoryDao.delete(conversationId)
}

// ─── PERSONA REPOSITORY ───────────────────────────────────────────────────────

@Singleton
class PersonaRepository @Inject constructor(
    private val personaConfigDao: PersonaConfigDao,
) {
    fun observe() = personaConfigDao.observe()

    suspend fun get() = personaConfigDao.get() ?: defaultPersona()

    suspend fun update(aiName: String? = null, userName: String? = null,
                       userIconPath: String? = null, accentColor: String? = null) {
        val current = get()
        personaConfigDao.upsert(
            current.copy(
                aiName       = aiName       ?: current.aiName,
                userName     = userName     ?: current.userName,
                userIconPath = userIconPath ?: current.userIconPath,
                accentColor  = accentColor  ?: current.accentColor,
            )
        )
    }

    private fun defaultPersona() = com.assistant.core.database.entity.PersonaConfigEntity(
        id = 1,
        aiName = "Assistant",
        userName = "User",
        userIconPath = null,
        accentColor = "#43B8C4",
    )
}
