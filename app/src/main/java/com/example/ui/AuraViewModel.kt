package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuraDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.preferences.AuraSettings
import com.example.data.preferences.SettingsManager
import com.example.data.repository.AuraRepository
import com.example.engine.DeviceMemoryStatus
import com.example.engine.DownloadEvent
import com.example.engine.DownloadTask
import com.example.engine.DownloadableModel
import com.example.engine.DownloadedModelInfo
import com.example.engine.GenerationMetrics
import com.example.engine.GgufMetadata
import com.example.engine.GgufParser
import com.example.engine.LlamaInferenceEngine
import com.example.engine.ModelCatalog
import com.example.engine.ModelDownloadManager
import com.example.engine.ModelLoadingState
import com.example.engine.ModelManager
import com.example.engine.ModelPreset
import com.example.engine.NetworkMonitor
import com.example.engine.StorageSpaceInfo
import com.example.engine.ToolCategory
import com.example.engine.ToolCategoryCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    HUB,
    CHAT
}

class AuraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuraRepository
    private val settingsManager: SettingsManager = SettingsManager(application)
    val modelManager: ModelManager = ModelManager(application)
    val downloadManager: ModelDownloadManager = ModelDownloadManager(application)
    private val inferenceEngine: LlamaInferenceEngine = LlamaInferenceEngine()
    private val networkMonitor: NetworkMonitor = NetworkMonitor(application)

    val settings: StateFlow<AuraSettings> = settingsManager.settings
    val modelLoadingState: StateFlow<ModelLoadingState> = modelManager.loadingState
    val activeMetadata: StateFlow<GgufMetadata> = modelManager.activeMetadata
    val activePreset: StateFlow<ModelPreset?> = modelManager.activePreset

    val downloadTasks: StateFlow<Map<String, DownloadTask>> = downloadManager.downloadTasks
    val downloadedModels: StateFlow<List<DownloadedModelInfo>> = downloadManager.downloadedModels
    val storageInfo: StateFlow<StorageSpaceInfo> = downloadManager.storageInfo

    // Network status
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), networkMonitor.checkOnline())

    private val _isWebSearchEnabled = MutableStateFlow(true)
    val isWebSearchEnabled: StateFlow<Boolean> = _isWebSearchEnabled.asStateFlow()

    // Navigation & Category Screen state
    private val _currentScreen = MutableStateFlow(AppScreen.CHAT)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _activeCategory = MutableStateFlow<ToolCategory?>(null)
    val activeCategory: StateFlow<ToolCategory?> = _activeCategory.asStateFlow()

    private val _downloadNotification = MutableStateFlow<DownloadedModelInfo?>(null)
    val downloadNotification: StateFlow<DownloadedModelInfo?> = _downloadNotification.asStateFlow()

    private val _deviceMemory = MutableStateFlow(modelManager.getDeviceMemoryStatus())
    val deviceMemory: StateFlow<DeviceMemoryStatus> = _deviceMemory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    private val _activeConversation = MutableStateFlow<ConversationEntity?>(null)
    val activeConversation: StateFlow<ConversationEntity?> = _activeConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    // Streaming state
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _currentMetrics = MutableStateFlow<GenerationMetrics?>(null)
    val currentMetrics: StateFlow<GenerationMetrics?> = _currentMetrics.asStateFlow()

    private var streamingJob: Job? = null

    val allConversations: StateFlow<List<ConversationEntity>>

    init {
        val db = AuraDatabase.getDatabase(application)
        repository = AuraRepository(db)

        allConversations = combine(repository.allConversations, _searchQuery) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { it.title.contains(query, ignoreCase = true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Listen for download completions
        viewModelScope.launch {
            downloadManager.downloadEvents.collect { event ->
                when (event) {
                    is DownloadEvent.Completed -> {
                        val matchingModel = downloadManager.downloadedModels.value.firstOrNull {
                            it.fileName == event.fileName
                        } ?: DownloadedModelInfo(
                            fileName = event.fileName,
                            filePath = event.file.absolutePath,
                            fileSizeBytes = event.file.length(),
                            fileSizeFormatted = "${event.file.length() / (1024 * 1024)} MB",
                            lastModified = System.currentTimeMillis()
                        )
                        _downloadNotification.value = matchingModel
                    }
                    is DownloadEvent.Failed -> {}
                }
            }
        }

        // Initial setup
        viewModelScope.launch {
            val defaultPreset = ModelCatalog.getDefaultPreset()
            modelManager.loadModel(
                metadata = defaultPreset.toGgufMetadata(),
                preset = defaultPreset,
                gpuLayers = settings.value.gpuLayers,
                threads = settings.value.cpuThreads,
                contextSize = settings.value.contextSize
            )

            val firstConv = repository.allConversations.firstOrNull()?.firstOrNull()
            if (firstConv != null) {
                selectConversation(firstConv.id)
            } else {
                createNewConversation("Yeni Sohbet")
            }
        }
    }

    fun navigateToChat(category: ToolCategory? = null) {
        _activeCategory.value = category
        if (category != null) {
            createNewConversation(category.title)
        }
        _currentScreen.value = AppScreen.CHAT
    }

    fun navigateToHub() {
        _currentScreen.value = AppScreen.HUB
    }

    fun toggleWebSearch() {
        _isWebSearchEnabled.value = !_isWebSearchEnabled.value
    }

    fun dismissDownloadNotification() {
        _downloadNotification.value = null
    }

    fun startModelDownload(model: DownloadableModel) {
        downloadManager.startDownload(model)
    }

    fun startCustomModelDownload(name: String, url: String) {
        downloadManager.startCustomDownload(name, url)
    }

    fun cancelModelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    fun deleteDownloadedModel(fileName: String) {
        downloadManager.deleteDownloadedModel(fileName)
    }

    fun refreshDownloads() {
        downloadManager.refreshDownloadedModels()
    }

    fun loadDownloadedModel(info: DownloadedModelInfo) {
        viewModelScope.launch {
            val matchingPreset = info.matchingPreset
            val matchingDl = info.matchingDownloadable
            val meta = if (matchingPreset != null) {
                matchingPreset.toGgufMetadata().copy(fileName = info.filePath)
            } else if (matchingDl != null) {
                matchingDl.toGgufMetadata(localFilePath = info.filePath)
            } else {
                GgufParser.createHeuristicMetadata(
                    fileName = info.fileName,
                    fileSize = info.fileSizeBytes
                ).copy(fileName = info.filePath)
            }

            modelManager.loadModel(
                metadata = meta,
                preset = matchingPreset,
                gpuLayers = settings.value.gpuLayers,
                threads = settings.value.cpuThreads,
                contextSize = settings.value.contextSize
            )
            refreshMemoryStatus()
            _downloadNotification.value = null
        }
    }

    fun refreshMemoryStatus() {
        _deviceMemory.value = modelManager.getDeviceMemoryStatus()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
        viewModelScope.launch {
            _activeConversation.value = repository.getConversationById(id)
            repository.getMessagesForConversation(id).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun createNewConversation(title: String = "Yeni Sohbet") {
        viewModelScope.launch {
            val meta = activeMetadata.value
            val convId = repository.createConversation(
                title = title,
                modelName = meta.modelName,
                systemPrompt = _activeCategory.value?.systemPrompt ?: settings.value.systemPrompt
            )
            selectConversation(convId)
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(id, newTitle)
            if (_activeConversationId.value == id) {
                _activeConversation.value = _activeConversation.value?.copy(title = newTitle)
            }
        }
    }

    fun togglePinConversation(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinConversation(id, isPinned)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                val remaining = allConversations.value.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    createNewConversation("Yeni Sohbet")
                }
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            createNewConversation("Yeni Sohbet")
        }
    }

    fun loadPresetModel(preset: ModelPreset) {
        viewModelScope.launch {
            modelManager.loadModel(
                metadata = preset.toGgufMetadata(),
                preset = preset,
                gpuLayers = settings.value.gpuLayers,
                threads = settings.value.cpuThreads,
                contextSize = settings.value.contextSize
            )
            refreshMemoryStatus()
        }
    }

    fun loadGgufFromUri(uri: Uri) {
        viewModelScope.launch {
            _deviceMemory.value = modelManager.getDeviceMemoryStatus()
            val result = GgufParser.parseFromUri(getApplication(), uri)
            result.onSuccess { metadata ->
                modelManager.loadModel(
                    metadata = metadata,
                    preset = null,
                    gpuLayers = settings.value.gpuLayers,
                    threads = settings.value.cpuThreads,
                    contextSize = settings.value.contextSize
                )
            }.onFailure { _ ->
                val fallbackMeta = GgufParser.createHeuristicMetadata(
                    fileName = uri.lastPathSegment ?: "custom_model.gguf",
                    fileSize = 1024L * 1024 * 700
                )
                modelManager.loadModel(
                    metadata = fallbackMeta,
                    preset = null,
                    gpuLayers = settings.value.gpuLayers,
                    threads = settings.value.cpuThreads,
                    contextSize = settings.value.contextSize
                )
            }
            refreshMemoryStatus()
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isStreaming.value) return

        val convId = _activeConversationId.value ?: return

        viewModelScope.launch {
            // Check if this is the first message to give chat a title
            val currentMsgs = _messages.value
            if (currentMsgs.isEmpty() || _activeConversation.value?.title == "Yeni Sohbet") {
                val autoTitle = if (userText.length > 28) userText.take(28) + "..." else userText
                renameConversation(convId, autoTitle)
            }

            // Insert User Message
            val userMsg = ChatMessageEntity(
                conversationId = convId,
                role = "user",
                content = userText.trim(),
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(userMsg)

            // Start Assistant Generation
            generateAssistantResponse(convId, userText.trim())
        }
    }

    private fun generateAssistantResponse(convId: Long, prompt: String) {
        streamingJob?.cancel()
        _isStreaming.value = true
        _streamingContent.value = ""
        _currentMetrics.value = null

        val currentMeta = activeMetadata.value
        val currentPres = activePreset.value
        val currentSet = settings.value
        val category = _activeCategory.value
        val isOnlineNow = isOnline.value
        val webSearchOn = _isWebSearchEnabled.value
        val history = _messages.value

        streamingJob = viewModelScope.launch {
            try {
                val stringBuilder = StringBuilder()
                var finalMetrics: GenerationMetrics? = null

                inferenceEngine.generateStream(
                    prompt = prompt,
                    systemPrompt = category?.systemPrompt ?: currentSet.systemPrompt,
                    chatHistory = history,
                    metadata = currentMeta,
                    preset = currentPres,
                    category = category,
                    isOnline = isOnlineNow,
                    isWebSearchEnabled = webSearchOn,
                    temperature = currentSet.temperature,
                    topP = currentSet.topP,
                    topK = currentSet.topK,
                    gpuLayers = currentSet.gpuLayers,
                    threads = currentSet.cpuThreads,
                    contextLimit = currentSet.contextSize
                ).collect { tokenChunk ->
                    stringBuilder.append(tokenChunk.token)
                    _streamingContent.value = stringBuilder.toString()

                    if (tokenChunk.metrics != null) {
                        finalMetrics = tokenChunk.metrics
                        _currentMetrics.value = tokenChunk.metrics
                    }
                }

                // Finalize and insert assistant message
                val responseText = stringBuilder.toString()
                if (responseText.isNotBlank()) {
                    val assistantMsg = ChatMessageEntity(
                        conversationId = convId,
                        role = "assistant",
                        content = responseText,
                        timestamp = System.currentTimeMillis(),
                        tokensCount = finalMetrics?.generatedTokens ?: (responseText.length / 4),
                        generationTimeMs = finalMetrics?.totalTimeMs ?: 0L,
                        tokensPerSecond = finalMetrics?.tokensPerSecond ?: 0f,
                        modelUsed = currentMeta.modelName
                    )
                    repository.insertMessage(assistantMsg)
                }
            } catch (e: CancellationException) {
                val responseText = _streamingContent.value
                if (responseText.isNotBlank()) {
                    val assistantMsg = ChatMessageEntity(
                        conversationId = convId,
                        role = "assistant",
                        content = "$responseText [Durduruldu]",
                        timestamp = System.currentTimeMillis(),
                        tokensCount = responseText.length / 4,
                        generationTimeMs = 0L,
                        tokensPerSecond = 0f,
                        modelUsed = currentMeta.modelName
                    )
                    repository.insertMessage(assistantMsg)
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = "Hata: ${e.message ?: "Model çıkarımı sırasında bir sorun oluştu."}",
                    timestamp = System.currentTimeMillis(),
                    modelUsed = currentMeta.modelName
                )
                repository.insertMessage(errorMsg)
            } finally {
                _isStreaming.value = false
                _streamingContent.value = ""
                _currentMetrics.value = null
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        _isStreaming.value = false
    }

    fun regenerateLastResponse() {
        if (_isStreaming.value) return
        val currentMsgs = _messages.value
        if (currentMsgs.isEmpty()) return

        val lastUserMsg = currentMsgs.lastOrNull { it.role == "user" } ?: return
        val lastMsg = currentMsgs.last()
        val convId = _activeConversationId.value ?: return

        viewModelScope.launch {
            if (lastMsg.role == "assistant") {
                repository.deleteMessage(lastMsg.id)
            }
            generateAssistantResponse(convId, lastUserMsg.content)
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun updateSettings(newSettings: AuraSettings) {
        settingsManager.updateSettings(newSettings)
    }

    fun resetSettings() {
        settingsManager.resetToDefaults()
    }

    fun exportActiveConversationAsMarkdown(): String {
        val conv = _activeConversation.value ?: return ""
        val msgs = _messages.value
        val sb = StringBuilder()
        sb.append("# ${conv.title}\n\n")
        sb.append("**Model:** ${conv.modelName}\n")
        sb.append("**Tarih:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conv.createdAt))}\n\n")
        sb.append("---\n\n")
        for (m in msgs) {
            val role = if (m.role == "user") "### 👤 Kullanıcı" else "### 🌐 LLM WORLD (${m.modelUsed.ifEmpty { conv.modelName }})"
            sb.append("$role\n")
            sb.append("${m.content}\n\n")
            if (m.tokensPerSecond > 0) {
                sb.append("_${String.format("%.1f", m.tokensPerSecond)} t/s • ${m.tokensCount} tokens_\n\n")
            }
        }
        return sb.toString()
    }
}
