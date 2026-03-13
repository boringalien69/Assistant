package com.assistant.core.database.dao

import androidx.room.*
import com.assistant.core.database.entity.*
import kotlinx.coroutines.flow.Flow

// ─── CONVERSATION DAO ─────────────────────────────────────────────────────────

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY is_pinned DESC, pin_order ASC, last_active_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE is_pinned = 1 ORDER BY pin_order ASC")
    fun observePinned(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE is_pinned = 0 ORDER BY last_active_at DESC")
    fun observeUnpinned(): Flow<List<ConversationEntity>>

    @Query("SELECT COUNT(*) FROM conversations WHERE is_pinned = 1")
    suspend fun pinnedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE conversations SET last_active_at = :timestamp, message_count = message_count + 1 WHERE id = :id")
    suspend fun touchConversation(id: String, timestamp: Long)

    @Query("UPDATE conversations SET is_pinned = :pinned, pin_order = :order WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, order: Int)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    // Auto-delete: chats inactive for 10+ days and not pinned
    @Query("SELECT * FROM conversations WHERE is_pinned = 0 AND last_active_at < :cutoffMs")
    suspend fun getExpiredConversations(cutoffMs: Long): List<ConversationEntity>

    @Query("UPDATE conversations SET expiry_notified = 1 WHERE id = :id")
    suspend fun markExpiryNotified(id: String)

    @Query("SELECT * FROM conversations WHERE title LIKE :query")
    suspend fun searchByTitle(query: String): List<ConversationEntity>
}

// ─── MESSAGE DAO ──────────────────────────────────────────────────────────────

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

// ─── CONTEXT MEMORY DAO ───────────────────────────────────────────────────────

@Dao
interface ContextMemoryDao {
    @Query("SELECT * FROM context_memory WHERE conversation_id = :conversationId")
    suspend fun get(conversationId: String): ContextMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ContextMemoryEntity)

    @Query("DELETE FROM context_memory WHERE conversation_id = :conversationId")
    suspend fun delete(conversationId: String)
}

// ─── MODEL CONFIG DAO ─────────────────────────────────────────────────────────

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs ORDER BY last_used_at DESC")
    fun observeAll(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE model_path = :path")
    suspend fun getByPath(path: String): ModelConfigEntity?

    @Query("SELECT * FROM model_configs WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveModel(): ModelConfigEntity?

    @Query("SELECT * FROM model_configs WHERE is_active = 1 LIMIT 1")
    fun observeActiveModel(): Flow<ModelConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ModelConfigEntity)

    @Update
    suspend fun update(config: ModelConfigEntity)

    @Query("UPDATE model_configs SET is_active = 0")
    suspend fun clearActive()

    @Query("UPDATE model_configs SET is_active = 1 WHERE model_path = :path")
    suspend fun setActive(path: String)

    @Query("DELETE FROM model_configs WHERE model_path = :path")
    suspend fun delete(path: String)
}

// ─── DOWNLOAD TASK DAO ────────────────────────────────────────────────────────

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY status ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET bytes_downloaded = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, status: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED','ACTIVE','PAUSED')")
    suspend fun getActiveDownloads(): List<DownloadTaskEntity>
}

// ─── PERSONA CONFIG DAO ───────────────────────────────────────────────────────

@Dao
interface PersonaConfigDao {
    @Query("SELECT * FROM persona_config WHERE id = 1")
    fun observe(): Flow<PersonaConfigEntity?>

    @Query("SELECT * FROM persona_config WHERE id = 1")
    suspend fun get(): PersonaConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: PersonaConfigEntity)
}
