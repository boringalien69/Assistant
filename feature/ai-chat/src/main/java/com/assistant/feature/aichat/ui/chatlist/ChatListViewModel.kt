package com.assistant.feature.aichat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.core.database.entity.ConversationEntity
import com.assistant.feature.aichat.data.LlamaEngine
import com.assistant.feature.aichat.data.local.ChatRepository
import com.assistant.feature.aichat.data.local.ModelRepository
import com.assistant.feature.aichat.domain.usecase.ChatLifecycleUseCase
import com.assistant.feature.aichat.domain.usecase.DetectDuplicateChatUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val pinned: List<ConversationEntity>    = emptyList(),
    val trending: ConversationEntity?       = null,
    val all: List<ConversationEntity>       = emptyList(),
    val expiringChats: List<ConversationEntity> = emptyList(),
    val isModelLoaded: Boolean              = false,
    val activeModelName: String             = "",
    val duplicateSuggestions: List<DetectDuplicateChatUseCase.DuplicateCandidate> = emptyList(),
    val showDuplicateBanner: Boolean        = false,
    val error: String?                      = null,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val llamaEngine: LlamaEngine,
    private val chatLifecycleUseCase: ChatLifecycleUseCase,
    private val detectDuplicateUseCase: DetectDuplicateChatUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        observeConversations()
        observeModel()
        processLifecycle()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                val pinned   = conversations.filter { it.isPinned }.sortedBy { it.pinOrder }
                val unpinned = conversations.filter { !it.isPinned }
                val trending = computeTrending(unpinned)
                val allList  = unpinned.sortedByDescending { it.lastActiveAt }
                val expiring = chatLifecycleUseCase.getChatsApproachingExpiry()

                _uiState.update { it.copy(
                    pinned = pinned,
                    trending = trending,
                    all = allList,
                    expiringChats = expiring,
                ) }
            }
        }
    }

    private fun observeModel() {
        viewModelScope.launch {
            modelRepository.observeActiveModel().collect { model ->
                _uiState.update { it.copy(
                    isModelLoaded = model != null && llamaEngine.isLoaded,
                    activeModelName = model?.displayName ?: "",
                ) }
            }
        }
    }

    private fun processLifecycle() {
        viewModelScope.launch {
            chatLifecycleUseCase.processExpiredChats()
        }
    }

    private fun computeTrending(unpinned: List<ConversationEntity>): ConversationEntity? {
        if (unpinned.isEmpty()) return null
        val now = System.currentTimeMillis()
        val maxAge = 48 * 60 * 60 * 1000L // 48 hours in ms

        return unpinned.maxByOrNull { conv ->
            val recencyWeight = if (now - conv.lastActiveAt < maxAge) {
                1f - ((now - conv.lastActiveAt).toFloat() / maxAge)
            } else 0f
            conv.messageCount * 0.6f + recencyWeight * 0.4f
        }
    }

    fun onNewChatTap(onSuggestionsFound: (List<DetectDuplicateChatUseCase.DuplicateCandidate>) -> Unit) {
        viewModelScope.launch {
            // Trigger 1: check existing titles on new chat tap
            val recentTitle = _uiState.value.all.firstOrNull()?.title ?: return@launch
            val suggestions = detectDuplicateUseCase.execute(recentTitle)
            if (suggestions.isNotEmpty()) {
                onSuggestionsFound(suggestions)
            }
        }
    }

    fun onFirstMessageTyped(text: String) {
        if (text.length < 10) return
        viewModelScope.launch {
            val suggestions = detectDuplicateUseCase.execute(text)
            _uiState.update { it.copy(
                duplicateSuggestions = suggestions,
                showDuplicateBanner = suggestions.isNotEmpty(),
            ) }
        }
    }

    fun dismissDuplicateBanner() {
        _uiState.update { it.copy(showDuplicateBanner = false, duplicateSuggestions = emptyList()) }
    }

    fun pinChat(chatId: String) {
        viewModelScope.launch {
            if (chatRepository.getPinnedCount() >= 3) {
                _uiState.update { it.copy(error = "MAX PINS REACHED — UNPIN ONE TO CONTINUE") }
                return@launch
            }
            val order = (_uiState.value.pinned.size)
            chatRepository.pinConversation(chatId, true, order)
        }
    }

    fun unpinChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.pinConversation(chatId, false, 0)
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameConversation(chatId, newTitle.take(60))
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(chatId)
        }
    }

    fun keepChat(chatId: String) {
        viewModelScope.launch {
            // Reset inactivity by touching — saves a new timestamp
            chatRepository.saveMessage(chatId, "system", "__keep__")
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
