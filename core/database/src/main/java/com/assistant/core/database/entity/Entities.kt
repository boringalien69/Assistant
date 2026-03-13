package com.assistant.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─── CONVERSATIONS ────────────────────────────────────────────────────────────

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "model_path") val modelPath: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "pin_color") val pinColor: String? = null,
    @ColumnInfo(name = "pin_order") val pinOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long,
    @ColumnInfo(name = "expiry_notified") val expiryNotified: Boolean = false,
    @ColumnInfo(name = "message_count") val messageCount: Int = 0,
)

// ─── MESSAGES ─────────────────────────────────────────────────────────────────

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversation_id")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String,       // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long,
    @ColumnInfo(name = "is_partial") val isPartial: Boolean = false,
)

// ─── CONTEXT MEMORY ───────────────────────────────────────────────────────────

@Entity(tableName = "context_memory")
data class ContextMemoryEntity(
    @PrimaryKey @ColumnInfo(name = "conversation_id") val conversationId: String,
    val excerpt: String,
    @ColumnInfo(name = "token_count") val tokenCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

// ─── MODEL CONFIGS ────────────────────────────────────────────────────────────

@Entity(tableName = "model_configs")
data class ModelConfigEntity(
    @PrimaryKey @ColumnInfo(name = "model_path") val modelPath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String = "",
    @ColumnInfo(name = "context_size") val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    @ColumnInfo(name = "top_p") val topP: Float = 0.9f,
    @ColumnInfo(name = "top_k") val topK: Int = 40,
    @ColumnInfo(name = "max_new_tokens") val maxNewTokens: Int = 512,
    @ColumnInfo(name = "n_threads") val nThreads: Int = 0,  // 0 = auto (half cores)
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long = 0L,
)

// ─── DOWNLOAD TASKS ───────────────────────────────────────────────────────────

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    @ColumnInfo(name = "dest_path") val destPath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "bytes_downloaded") val bytesDownloaded: Long = 0L,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long = -1L,
    val status: String = "QUEUED",  // QUEUED | ACTIVE | PAUSED | COMPLETE | FAILED
    @ColumnInfo(name = "worker_id") val workerId: String? = null,
)

// ─── PERSONA CONFIG ───────────────────────────────────────────────────────────

@Entity(tableName = "persona_config")
data class PersonaConfigEntity(
    @PrimaryKey val id: Int = 1,   // singleton row
    @ColumnInfo(name = "ai_name") val aiName: String = "Assistant",
    @ColumnInfo(name = "user_name") val userName: String = "User",
    @ColumnInfo(name = "user_icon_path") val userIconPath: String? = null,
    @ColumnInfo(name = "accent_color") val accentColor: String = "#43B8C4",
)
