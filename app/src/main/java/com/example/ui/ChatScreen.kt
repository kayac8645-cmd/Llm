package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.DownloadStatus
import com.example.engine.DownloadedModelInfo
import com.example.engine.ModelCategory
import com.example.engine.ModelCatalog
import com.example.engine.ToolCategory
import com.example.engine.ToolCategoryCatalog
import com.example.ui.components.BenchmarkSheet
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.ConversationsDrawerContent
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.StreamingAssistantBubble
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OledBackground
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledBorderSubtle
import com.example.ui.theme.OledSurface
import com.example.ui.theme.OledSurfaceCard
import com.example.ui.theme.OledSurfaceElevated
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AuraViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val conversations by viewModel.allConversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val activeConv by viewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val streamingContent by viewModel.streamingContent.collectAsStateWithLifecycle()
    val activeMetadata by viewModel.activeMetadata.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val loadingState by viewModel.modelLoadingState.collectAsStateWithLifecycle()
    val memoryStatus by viewModel.deviceMemory.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isWebSearchEnabled by viewModel.isWebSearchEnabled.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()

    // Download manager states
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloadedModels by viewModel.downloadedModels.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val downloadNotification by viewModel.downloadNotification.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var selectedModelCategory by remember { mutableStateOf<ModelCategory?>(null) }

    // Bottom Sheets State
    var showModelSheet by remember { mutableStateOf(false) }
    var modelSheetInitialTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBenchmarkSheet by remember { mutableStateOf(false) }

    val modelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val benchmarkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto-scroll to bottom on new message or streaming tokens
    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty() || streamingContent.isNotEmpty()) {
            listState.animateScrollToItem((messages.size + if (isStreaming) 1 else 0).coerceAtLeast(0))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ConversationsDrawerContent(
                conversations = conversations,
                activeConversationId = activeConvId,
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                },
                onNewChat = {
                    viewModel.createNewConversation("Yeni Sohbet")
                },
                onRenameConversation = { id, title ->
                    viewModel.renameConversation(id, title)
                },
                onTogglePin = { id, isPinned ->
                    viewModel.togglePinConversation(id, isPinned)
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onClearAll = {
                    viewModel.clearAllConversations()
                },
                onOpenModelHub = {
                    viewModel.refreshMemoryStatus()
                    modelSheetInitialTab = 1
                    showModelSheet = true
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = OledBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column {
                    ChatGPTTopBar(
                        activeMetadataName = activeMetadata.modelName,
                        quantization = activeMetadata.quantization,
                        isOnline = isOnline,
                        isWebSearchEnabled = isWebSearchEnabled,
                        activeCategory = activeCategory,
                        onBackToHub = {
                            viewModel.navigateToHub()
                        },
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onOpenModelSelector = {
                            viewModel.refreshMemoryStatus()
                            modelSheetInitialTab = 0
                            showModelSheet = true
                        },
                        onOpenSettings = {
                            showSettingsSheet = true
                        },
                        onNewChat = {
                            viewModel.createNewConversation(activeCategory?.title ?: "Yeni Sohbet")
                        }
                    )

                    // Quick Model Category Filter Bar
                    CategoryFilterStrip(
                        selectedCategory = selectedModelCategory,
                        onCategorySelect = { cat ->
                            selectedModelCategory = if (selectedModelCategory == cat) null else cat
                            if (cat != null) {
                                // Find best matching preset for this category
                                val matchingPreset = ModelCatalog.PRESETS.firstOrNull { it.category == cat }
                                if (matchingPreset != null && matchingPreset.id != activePreset?.id) {
                                    viewModel.loadPresetModel(matchingPreset)
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                ChatGPTBottomInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    isStreaming = isStreaming,
                    isOnline = isOnline,
                    isWebSearchEnabled = isWebSearchEnabled,
                    onToggleWebSearch = {
                        viewModel.toggleWebSearch()
                    },
                    onOpenModelHub = {
                        viewModel.refreshMemoryStatus()
                        modelSheetInitialTab = 1
                        showModelSheet = true
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendMessage(textToSend)
                        }
                    },
                    onStop = {
                        viewModel.stopStreaming()
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(OledBackground)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Download Completion Notification Banner
                    AnimatedVisibility(
                        visible = downloadNotification != null,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 })
                    ) {
                        val notifModel = downloadNotification
                        if (notifModel != null) {
                            ChatDownloadSuccessBanner(
                                info = notifModel,
                                onActivate = { viewModel.loadDownloadedModel(notifModel) },
                                onDismiss = { viewModel.dismissDownloadNotification() }
                            )
                        }
                    }

                    if (messages.isEmpty() && !isStreaming) {
                        ChatGPTWelcomeState(
                            activeCategory = activeCategory,
                            modelName = activeMetadata.modelName,
                            isOnline = isOnline,
                            onCategoryPick = { cat ->
                                selectedModelCategory = cat
                                val matching = ModelCatalog.PRESETS.firstOrNull { it.category == cat }
                                if (matching != null) {
                                    viewModel.loadPresetModel(matching)
                                }
                            },
                            onSuggestionClick = { suggestion ->
                                viewModel.sendMessage(suggestion)
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                                val isLatestAssistant = index == messages.size - 1 && msg.role == "assistant"
                                ChatMessageItem(
                                    message = msg,
                                    isLatestAssistantMessage = isLatestAssistant,
                                    onRegenerate = { viewModel.regenerateLastResponse() },
                                    onDelete = { viewModel.deleteMessage(msg.id) }
                                )
                            }

                            if (isStreaming) {
                                item {
                                    StreamingAssistantBubble(
                                        content = streamingContent,
                                        modelName = activeMetadata.modelName,
                                        onStop = { viewModel.stopStreaming() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Model Selector Sheet
    if (showModelSheet) {
        ModelSelectorSheet(
            sheetState = modelSheetState,
            activeMetadata = activeMetadata,
            activePreset = activePreset,
            loadingState = loadingState,
            memoryStatus = memoryStatus,
            storageInfo = storageInfo,
            downloadTasks = downloadTasks,
            downloadedModels = downloadedModels,
            initialTab = modelSheetInitialTab,
            onSelectPreset = { preset ->
                viewModel.loadPresetModel(preset)
                showModelSheet = false
            },
            onSelectDownloadedModel = { info ->
                viewModel.loadDownloadedModel(info)
                showModelSheet = false
            },
            onStartDownload = { model ->
                viewModel.startModelDownload(model)
            },
            onStartCustomDownload = { name, url ->
                viewModel.startCustomModelDownload(name, url)
            },
            onCancelDownload = { id ->
                viewModel.cancelModelDownload(id)
            },
            onDeleteDownloadedModel = { fileName ->
                viewModel.deleteDownloadedModel(fileName)
            },
            onSelectUri = { uri ->
                viewModel.loadGgufFromUri(uri)
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false }
        )
    }

    // Settings Sheet
    if (showSettingsSheet) {
        SettingsSheet(
            sheetState = settingsSheetState,
            settings = settings,
            onUpdateSettings = { newSet ->
                viewModel.updateSettings(newSet)
            },
            onResetSettings = {
                viewModel.resetSettings()
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Benchmark Sheet
    if (showBenchmarkSheet) {
        BenchmarkSheet(
            sheetState = benchmarkSheetState,
            metadata = activeMetadata,
            memoryStatus = memoryStatus,
            threads = settings.cpuThreads,
            gpuLayers = settings.gpuLayers,
            onDismiss = { showBenchmarkSheet = false }
        )
    }
}

@Composable
private fun CategoryFilterStrip(
    selectedCategory: ModelCategory?,
    onCategorySelect: (ModelCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // All chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selectedCategory == null) CyanPrimary else OledSurfaceCard)
                .border(
                    1.dp,
                    if (selectedCategory == null) CyanPrimary else OledBorderSubtle,
                    RoundedCornerShape(16.dp)
                )
                .clickable { onCategorySelect(null) }
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Tümü",
                fontSize = 11.sp,
                fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedCategory == null) Color.Black else TextSecondary
            )
        }

        // Category items
        ModelCategory.values().forEach { category ->
            val isSelected = selectedCategory == category
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) CyanPrimary else OledSurfaceCard)
                    .border(
                        1.dp,
                        if (isSelected) CyanPrimary else OledBorderSubtle,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onCategorySelect(category) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = category.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.Black else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ChatGPTTopBar(
    activeMetadataName: String,
    quantization: String,
    isOnline: Boolean,
    isWebSearchEnabled: Boolean,
    activeCategory: ToolCategory?,
    onBackToHub: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    Surface(
        color = OledSurface,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawBehind {
                drawLine(
                    color = OledBorderSubtle,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Conversations Drawer Button & Tools Hub
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Sohbetler",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onBackToHub) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "Araçlar & Roller",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center: Minimalist ChatGPT Model Switcher Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OledSurfaceCard)
                    .border(1.dp, OledBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenModelSelector)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Glowing status light
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isOnline && isWebSearchEnabled) CyanPrimary else SuccessGreen)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (activeCategory != null) activeCategory.title else activeMetadataName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Right: New Chat and Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNewChat) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Yeni Sohbet",
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatGPTWelcomeState(
    activeCategory: ToolCategory?,
    modelName: String,
    isOnline: Boolean,
    onCategoryPick: (ModelCategory) -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_anim")
    val heroPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Centered Avatar / Icon with breathing glow
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(heroPulseScale)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        colors = if (activeCategory != null) {
                            listOf(activeCategory.startColor, activeCategory.endColor, activeCategory.startColor)
                        } else {
                            listOf(CyanPrimary, NeonViolet, ElectricBlue, CyanPrimary)
                        }
                    )
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activeCategory?.icon ?: Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = activeCategory?.title ?: "Bugün sana nasıl yardımcı olabilirim?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = activeCategory?.subtitle ?: "$modelName • %100 Cihaz İçi Gizlilik",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Suggested Prompt Chips (ChatGPT style)
        val prompts = activeCategory?.quickPrompts ?: listOf(
            "Kotlin Coroutines ve StateFlow ile temiz mimari kur",
            "DeepSeek R1 akıl yürütme ile karmaşık problemi çöz",
            "İngilizce profesyonel bir iş e-postasını Türkçe'ye çevir",
            "GGUF formatı ve Q4_K_M kuantizasyonunu açıkla"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(OledSurfaceCard)
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp))
                        .clickable { onSuggestionClick(prompt) }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatGPTBottomInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isStreaming: Boolean,
    isOnline: Boolean,
    isWebSearchEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    onOpenModelHub: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = OledSurface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .drawBehind {
                drawLine(
                    color = OledBorderSubtle,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Model Hub / Tools shortcut button
                IconButton(
                    onClick = onOpenModelHub,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Model İndirici",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Web Search Toggle button (Globe)
                IconButton(
                    onClick = onToggleWebSearch,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = if (isWebSearchEnabled) Icons.Default.Public else Icons.Default.PublicOff,
                            contentDescription = "Web Araması",
                            tint = if (isWebSearchEnabled && isOnline) CyanPrimary else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isWebSearchEnabled && isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary)
                            )
                        }
                    }
                }

                // Minimalist Rounded Input Box
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = {
                        Text(
                            text = if (isStreaming) "Yanıt üretiliyor..." else "ChatGPT'ye mesaj gönder...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    maxLines = 4,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = TextPrimary,
                        lineHeight = 19.sp
                    ),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OledSurfaceVariant,
                        unfocusedContainerColor = OledSurfaceVariant,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = OledBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Send or Stop circular button (ChatGPT style)
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ErrorRed)
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Durdur",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    val isEnabled = text.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) CyanPrimary else Color(0xFF1E293B)
                            )
                            .clickable(enabled = isEnabled, onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Gönder",
                            tint = if (isEnabled) Color.Black else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Subtitle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LLM WORLD • ARM64 GGUF v3 • %100 Cihaz İçi & Güvenli",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ChatDownloadSuccessBanner(
    info: DownloadedModelInfo,
    onActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OledSurfaceCard)
            .border(1.dp, SuccessGreen, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Model İndirildi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        text = "${info.matchingPreset?.name ?: info.matchingDownloadable?.name ?: info.fileName} (${info.fileSizeFormatted})",
                        fontSize = 11.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Yükle",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

