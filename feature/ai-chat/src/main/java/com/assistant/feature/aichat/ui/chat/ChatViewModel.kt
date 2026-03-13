package com.assistant.feature.aichat.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.core.database.entity.MessageEntity
import com.assistant.core.database.entity.PersonaConfigEntity
import com.assistant.feature.aichat.data.LlamaEngine
import com.assistant.feature.aichat.data.local.ChatRepository
import com.assistant.feature.aichat.data.local.ModelRepository
import com.assistant.feature.aichat.data.local.PersonaRepository
import com.assistant.feature.aichat.domain.usecase.BuildContextMemoryUseCase
import com.assistant.feature.aichat.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InferenceStatus { IDLE, PROCESSING, ERROR, NO_MODEL }

data class ChatUiState(
    val messages: List<MessageEntity>  = emptyList(),
    val streamingText: String          = "",
    val isStreaming: Boolean           = false,
    val status: InferenceStatus        = InferenceStatus.IDLE,
    val modelName: String              = "",
    val persona: PersonaConfigEntity?  = null,
    val error: String?                 = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val personaRepository: PersonaRepository,
    private val llamaEngine: LlamaEngine,
    private val sendMessageUseCase: SendMessageUseCase,
    private val buildContextMemoryUseCase: BuildContextMemoryUseCase,
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        observeModel()
        observePersona()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun observeModel() {
        viewModelScope.launch {
            modelRepository.observeActiveModel().collect { model ->
                _uiState.update { it.copy(
                    modelName = model?.displayName ?: "",
                    status = if (model != null && llamaEngine.isLoaded) InferenceStatus.IDLE
                             else InferenceStatus.NO_MODEL,
                ) }
            }
        }
    }

    private fun observePersona() {
        viewModelScope.launch {
            personaRepository.observe().collect { persona ->
                _uiState.update { it.copy(persona = persona) }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (_uiState.value.status == InferenceStatus.NO_MODEL) return
        if (_uiState.value.isStreaming) return

        viewModelScope.launch {
            _uiState.update { it.copy(
                isStreaming   = true,
                streamingText = "",
                status        = InferenceStatus.PROCESSING,
            ) }

            try {
                val streamingBuffer = StringBuilder()

                sendMessageUseCase.execute(chatId, text)
                    .collect { token ->
                        streamingBuffer.append(token)
                        _uiState.update { it.copy(streamingText = streamingBuffer.toString()) }
                    }

                // Save completed response
                val fullResponse = streamingBuffer.toString()
                sendMessageUseCase.saveAssistantResponse(chatId, fullResponse)

                // Update context memory after each response
                buildContextMemoryUseCase.execute(chatId)

                _uiState.update { it.copy(
                    isStreaming   = false,
                    streamingText = "",
                    status        = InferenceStatus.IDLE,
                ) }

            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isStreaming = false,
                    streamingText = "",
                    status = InferenceStatus.ERROR,
                    error = "INFERENCE FAILED — ${e.message?.uppercase() ?: "UNKNOWN ERROR"}",
                ) }
            }
        }
    }

    fun haltInference() {
        viewModelScope.launch {
            llamaEngine.cancelInference()
            val partial = _uiState.value.streamingText
            if (partial.isNotBlank()) {
                sendMessageUseCase.saveAssistantResponse(chatId, partial, isPartial = true)
            }
            _uiState.update { it.copy(
                isStreaming   = false,
                streamingText = "",
                status        = InferenceStatus.IDLE,
            ) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, status = InferenceStatus.IDLE) }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel destroyed — release native context to free RAM
        viewModelScope.launch {
            llamaEngine.releaseModel()
        }
    }
}
