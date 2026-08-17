package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.DownloadStatus
import com.example.engine.DownloadedModelInfo
import com.example.engine.ToolCategory
import com.example.engine.ToolCategoryCatalog
import com.example.ui.components.BenchmarkSheet
import com.example.ui.components.ConversationsDrawerContent
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.HeroBannerEnd
import com.example.ui.theme.HeroBannerStart
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OledBackground
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledBorderSubtle
import com.example.ui.theme.OledSurface
import com.example.ui.theme.OledSurfaceCard
import com.example.ui.theme.OledSurfaceElevated
import com.example.ui.theme.ProGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(
    viewModel: AuraViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val conversations by viewModel.allConversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val activeMetadata by viewModel.activeMetadata.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val loadingState by viewModel.modelLoadingState.collectAsStateWithLifecycle()
    val memoryStatus by viewModel.deviceMemory.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    // Download manager states
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloadedModels by viewModel.downloadedModels.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val downloadNotification by viewModel.downloadNotification.collectAsStateWithLifecycle()

    val hasActiveDownloads = downloadTasks.values.any {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var modelSheetInitialTab by remember { mutableIntStateOf(1) } // 1: Model Hub
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBenchmarkSheet by remember { mutableStateOf(false) }

    val modelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val benchmarkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    viewModel.navigateToChat(null)
                },
                onNewChat = {
                    viewModel.createNewConversation("Yeni Sohbet")
                    viewModel.navigateToChat(null)
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                HubTopBar(
                    hasActiveDownloads = hasActiveDownloads,
                    isOnline = isOnline,
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onOpenModelHub = {
                        viewModel.refreshMemoryStatus()
                        modelSheetInitialTab = 1
                        showModelSheet = true
                    },
                    onOpenBenchmark = {
                        showBenchmarkSheet = true
                    },
                    onOpenSettings = {
                        showSettingsSheet = true
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
                            HubDownloadNotificationBanner(
                                info = notifModel,
                                onActivate = {
                                    viewModel.loadDownloadedModel(notifModel)
                                    viewModel.navigateToChat(null)
                                },
                                onDismiss = { viewModel.dismissDownloadNotification() }
                            )
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        // Big Hero Card: "Yapay Zeka Sohbeti"
                        item(span = { GridItemSpan(2) }) {
                            HeroChatCard(
                                isOnline = isOnline,
                                activeModelName = activeMetadata.modelName,
                                onStartChat = {
                                    viewModel.navigateToChat(null)
                                }
                            )
                        }

                        // Section Title: "Araçlar"
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Araçlar",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                // Real-time Internet & Offline Hybrid Indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isOnline) CyanPrimary.copy(alpha = 0.12f) else OledSurfaceCard)
                                        .border(
                                            1.dp,
                                            if (isOnline) CyanPrimary.copy(alpha = 0.4f) else OledBorderSubtle,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = if (isOnline) CyanPrimary else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isOnline) "Çevrimiçi (Web Destekli)" else "Lokal Motor (GGUF)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isOnline) CyanPrimary else TextSecondary
                                    )
                                }
                            }
                        }

                        // 2-Column Tools Grid from the Screenshots
                        items(ToolCategoryCatalog.categories, key = { it.id }) { tool ->
                            ToolCategoryCard(
                                tool = tool,
                                onClick = {
                                    viewModel.navigateToChat(tool)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Selector & Hub Sheet
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
private fun HubTopBar(
    hasActiveDownloads: Boolean,
    isOnline: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelHub: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenSettings: () -> Unit
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: App Name "LLM World"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenDrawer)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OledSurfaceCard)
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menü",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "LLM World",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            // Right: GitHub Pill & Action Buttons (matching screenshot)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GitHub Star Pill Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OledSurfaceCard)
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = ProGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "★ 547",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Download Hub Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasActiveDownloads) CyanPrimary.copy(alpha = 0.2f) else OledSurfaceCard)
                        .border(
                            1.dp,
                            if (hasActiveDownloads) CyanPrimary else OledBorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(onClick = onOpenModelHub),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Model İndirici",
                        tint = if (hasActiveDownloads) CyanPrimary else TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Speed / Benchmark Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OledSurfaceCard)
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenBenchmark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Hız Testi",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OledSurfaceCard)
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChatCard(
    isOnline: Boolean,
    activeModelName: String,
    onStartChat: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        HeroBannerStart,
                        HeroBannerEnd
                    )
                )
            )
            .clickable(onClick = onStartChat)
            .padding(22.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Yapay Zeka Sohbeti",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$activeModelName • ${if (isOnline) "Hibrit Web Aktif" else "Lokal Motor"}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // "Şimdi sohbet et" button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable(onClick = onStartChat)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Şimdi sohbet et",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ToolCategoryCard(
    tool: ToolCategory,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(tool.startColor, tool.endColor)
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Icon and optional Lock badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (tool.isLocked) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ProGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Pro",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Bottom Text: Title
            Text(
                text = tool.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HubDownloadNotificationBanner(
    info: DownloadedModelInfo,
    onActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OledSurfaceCard)
            .border(1.dp, SuccessGreen, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Model İndirildi",
                        fontSize = 12.sp,
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Aktif Et",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
