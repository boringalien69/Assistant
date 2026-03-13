package com.assistant.feature.aichat.ui.model

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.core.database.entity.DownloadTaskEntity
import com.assistant.core.database.entity.ModelConfigEntity
import com.assistant.feature.aichat.data.LlamaEngine
import com.assistant.feature.aichat.data.ModelCatalog
import com.assistant.feature.aichat.data.local.DownloadRepository
import com.assistant.feature.aichat.data.local.ModelRepository
import com.assistant.feature.aichat.domain.usecase.LoadModelUseCase
import com.assistant.feature.aichat.domain.usecase.StartDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── MODEL LIBRARY VIEW MODEL ─────────────────────────────────────────────────

data class ModelLibraryUiState(
    val models: List<ModelConfigEntity>     = emptyList(),
    val downloads: List<DownloadTaskEntity> = emptyList(),
    val activeModelPath: String?            = null,
    val isLoading: Boolean                  = false,
    val error: String?                      = null,
    val showDeleteConfirm: String?          = null,  // model path pending delete
    val showCustomUrlDialog: Boolean        = false,
    val availableRamBytes: Long             = 0L,
)

@HiltViewModel
class ModelLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val downloadRepository: DownloadRepository,
    private val llamaEngine: LlamaEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelLibraryUiState())
    val uiState: StateFlow<ModelLibraryUiState> = _uiState.asStateFlow()

    init {
        observeModels()
        observeDownloads()
        _uiState.update { it.copy(availableRamBytes = getAvailableRam()) }
    }

    private fun observeModels() {
        viewModelScope.launch {
            combine(
                modelRepository.observeAll(),
                modelRepository.observeActiveModel(),
            ) { models, active ->
                _uiState.update { it.copy(
                    models          = models,
                    activeModelPath = active?.modelPath,
                ) }
            }.collect()
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.observeAll().collect { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
            }
        }
    }

    fun loadModel(modelPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = loadModelUseCase.execute(modelPath)
            _uiState.update { it.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message,
            ) }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            llamaEngine.releaseModel()
        }
    }

    fun confirmDelete(modelPath: String) {
        _uiState.update { it.copy(showDeleteConfirm = modelPath) }
    }

    fun executeDelete(modelPath: String) {
        viewModelScope.launch {
            if (llamaEngine.loadedModelPath == modelPath) {
                llamaEngine.releaseModel()
            }
            File(modelPath).delete()
            modelRepository.delete(modelPath)
            _uiState.update { it.copy(showDeleteConfirm = null) }
        }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun renameModel(modelPath: String, newName: String) {
        viewModelScope.launch {
            val config = modelRepository.getByPath(modelPath) ?: return@launch
            modelRepository.update(config.copy(displayName = newName))
        }
    }

    fun downloadFromUrl(url: String, displayName: String) {
        viewModelScope.launch {
            val result = startDownloadUseCase.execute(url, displayName)
            result.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(showCustomUrlDialog = false) }
        }
    }

    fun pauseDownload(taskId: String) {
        viewModelScope.launch { startDownloadUseCase.pauseDownload(taskId) }
    }

    fun resumeDownload(taskId: String) {
        viewModelScope.launch { startDownloadUseCase.resumeDownload(taskId) }
    }

    fun cancelDownload(taskId: String) {
        viewModelScope.launch { startDownloadUseCase.cancelDownload(taskId) }
    }

    fun showCustomUrlDialog() {
        _uiState.update { it.copy(showCustomUrlDialog = true) }
    }

    fun hideCustomUrlDialog() {
        _uiState.update { it.copy(showCustomUrlDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getAvailableRam(): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }
}

// ─── MODEL PICKER VIEW MODEL ──────────────────────────────────────────────────

data class ModelPickerUiState(
    val bestFitId: String?              = null,
    val availableRamBytes: Long         = 0L,
    val activeDownloads: List<DownloadTaskEntity> = emptyList(),
)

@HiltViewModel
class ModelPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val startDownloadUseCase: StartDownloadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelPickerUiState())
    val uiState: StateFlow<ModelPickerUiState> = _uiState.asStateFlow()

    init {
        val availRam = getAvailableRam()
        val bestFit  = ModelCatalog.bestFit(availRam)
        _uiState.update { it.copy(
            availableRamBytes = availRam,
            bestFitId         = bestFit?.id,
        ) }

        viewModelScope.launch {
            downloadRepository.observeAll().collect { downloads ->
                _uiState.update { it.copy(activeDownloads = downloads) }
            }
        }
    }

    fun downloadCatalogEntry(entry: ModelCatalog.CatalogEntry) {
        viewModelScope.launch {
            startDownloadUseCase.execute(entry.downloadUrl, entry.displayName)
        }
    }

    fun downloadCustomUrl(url: String, displayName: String): Boolean {
        if (!StartDownloadUseCase.isValidGgufUrl(url)) return false
        viewModelScope.launch {
            startDownloadUseCase.execute(url, displayName)
        }
        return true
    }

    private fun getAvailableRam(): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }
}
