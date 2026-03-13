package com.assistant.feature.aichat.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.feature.aichat.data.LlamaEngine
import com.assistant.feature.aichat.data.local.ChatRepository
import com.assistant.feature.aichat.data.local.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val llamaEngine: LlamaEngine,
) : ViewModel() {

    fun createChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val modelPath = modelRepository.getActiveModel()?.modelPath ?: ""
            val chatId = chatRepository.createConversation(
                title     = "NEW THREAD",
                modelPath = modelPath,
            )
            onCreated(chatId)
        }
    }
}

@Composable
fun NewChatEntryPoint(
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.createChat(onCreated)
    }
}
