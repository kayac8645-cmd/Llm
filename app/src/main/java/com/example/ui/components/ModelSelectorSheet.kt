package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DeviceMemoryStatus
import com.example.engine.DownloadStatus
import com.example.engine.DownloadTask
import com.example.engine.DownloadableModel
import com.example.engine.DownloadedModelInfo
import com.example.engine.GgufMetadata
import com.example.engine.ModelCategory
import com.example.engine.ModelCatalog
import com.example.engine.ModelDownloadCatalog
import com.example.engine.ModelLoadingState
import com.example.engine.ModelPreset
import com.example.engine.StorageSpaceInfo
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanPrimaryDark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OledBackground
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledBorderSubtle
import com.example.ui.theme.OledSurfaceCard
import com.example.ui.theme.OledSurfaceElevated
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    sheetState: SheetState,
    activeMetadata: GgufMetadata,
    activePreset: ModelPreset?,
    loadingState: ModelLoadingState,
    memoryStatus: DeviceMemoryStatus,
    storageInfo: StorageSpaceInfo,
    downloadTasks: Map<String, DownloadTask>,
    downloadedModels: List<DownloadedModelInfo>,
    onSelectPreset: (ModelPreset) -> Unit,
    onSelectDownloadedModel: (DownloadedModelInfo) -> Unit,
    onStartDownload: (DownloadableModel) -> Unit,
    onStartCustomDownload: (String, String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownloadedModel: (String) -> Unit,
    onSelectUri: (Uri) -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ModelCategory?>(null) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectUri(uri)
        }
    }

    if (showCustomUrlDialog) {
        CustomModelDownloadDialog(
            onDismiss = { showCustomUrlDialog = false },
            onDownload = { name, url ->
                onStartCustomDownload(name, url)
                showCustomUrlDialog = false
                selectedTab = 1 // Switch to download hub
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(OledBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "GGUF Model Merkezi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "PocketPal Seviyesinde Çevrimdışı LLM Motoru",
                        fontSize = 12.sp,
                        color = CyanPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OledSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ARM64 GGUF v3",
                        color = CyanPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bento Segmented Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = OledSurfaceCard,
                contentColor = CyanPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyanPrimary,
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 0) CyanPrimary else TextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Yüklü & İndirilenler",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) TextPrimary else TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 1) CyanPrimary else TextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Model İndirici (Hub)",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) TextPrimary else TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Tab Content
            if (selectedTab == 0) {
                // TAB 0: INSTALLED / ACTIVE / PRESET MODELS
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Loading state if active
                    if (loadingState.status != ModelLoadingState.Status.LOADED &&
                        loadingState.status != ModelLoadingState.Status.UNLOADED
                    ) {
                        item {
                            LoadingProgressCard(loadingState)
                        }
                    }

                    // Active model details
                    item {
                        ActiveModelCard(activeMetadata, activePreset)
                    }

                    // Device RAM Card
                    item {
                        DeviceRamStatusCard(memoryStatus, activeMetadata.memoryEstimateMb)
                    }

                    // Downloaded Models on Storage Section
                    if (downloadedModels.isNotEmpty()) {
                        item {
                            Text(
                                text = "CİHAZDAKİ İNDİRİLMİŞ MODELLER (${downloadedModels.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        items(downloadedModels) { info ->
                            DownloadedModelCard(
                                info = info,
                                isCurrentlyLoaded = activeMetadata.fileName == info.filePath || activeMetadata.fileName == info.fileName,
                                onActivate = { onSelectDownloadedModel(info) },
                                onDelete = { onDeleteDownloadedModel(info.fileName) }
                            )
                        }
                    }

                    // Import from storage button
                    item {
                        ImportCustomGgufCard(
                            onSelectFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onOpenUrlDialog = { showCustomUrlDialog = true }
                        )
                    }

                    // Preset Models Catalog with category filter
                    item {
                        Column {
                            Text(
                                text = "HAZIR ŞABLON MODELLER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    label = "Tümü",
                                    isSelected = selectedCategory == null,
                                    onClick = { selectedCategory = null }
                                )
                                for (cat in ModelCategory.values()) {
                                    FilterChip(
                                        label = cat.displayName,
                                        isSelected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat }
                                    )
                                }
                            }
                        }
                    }

                    val filteredPresets = ModelCatalog.PRESETS.filter { preset ->
                        selectedCategory == null || preset.category == selectedCategory
                    }

                    items(filteredPresets) { preset ->
                        val isSelected = activePreset?.id == preset.id
                        PresetModelCard(
                            preset = preset,
                            isSelected = isSelected,
                            onClick = { onSelectPreset(preset) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                // TAB 1: MODEL DOWNLOAD HUB (HUGGING FACE)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Storage status bar
                    item {
                        StorageSpaceCard(storageInfo)
                    }

                    // Quick Action: Custom URL & HuggingFace Direct Link
                    item {
                        CustomDownloadPromptCard(
                            onClick = { showCustomUrlDialog = true }
                        )
                    }

                    // Search and Filter Bar
                    item {
                        Column {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text("Model veya yazar ara (Qwen, Llama, DeepSeek...)", color = TextMuted, fontSize = 12.sp)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Temizle",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OledSurfaceVariant,
                                    unfocusedContainerColor = OledSurfaceCard,
                                    focusedBorderColor = CyanPrimary,
                                    unfocusedBorderColor = OledBorderSubtle,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Category filter chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    label = "Tümü",
                                    isSelected = selectedCategory == null,
                                    onClick = { selectedCategory = null }
                                )
                                for (cat in ModelCategory.values()) {
                                    FilterChip(
                                        label = cat.displayName,
                                        isSelected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat }
                                    )
                                }
                            }
                        }
                    }

                    // Model Download List
                    val filteredModels = ModelDownloadCatalog.DOWNLOAD_PRESETS.filter { model ->
                        val matchesCategory = selectedCategory == null || model.category == selectedCategory
                        val matchesSearch = searchQuery.isBlank() ||
                            model.name.contains(searchQuery, ignoreCase = true) ||
                            model.author.contains(searchQuery, ignoreCase = true) ||
                            model.description.contains(searchQuery, ignoreCase = true)
                        matchesCategory && matchesSearch
                    }

                    if (filteredModels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Arama kriterine uygun model bulunamadı.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredModels) { model ->
                            val task = downloadTasks[model.id]
                            val isDownloaded = downloadedModels.any {
                                it.fileName.contains(model.id, ignoreCase = true) ||
                                it.fileName.equals(model.fileName, ignoreCase = true)
                            }
                            val isCurrentlyLoaded = isDownloaded && (
                                activeMetadata.fileName.contains(model.id, ignoreCase = true) ||
                                activeMetadata.modelName == model.name
                            )

                            DownloadableModelCard(
                                model = model,
                                task = task,
                                isDownloaded = isDownloaded,
                                isCurrentlyLoaded = isCurrentlyLoaded,
                                onStartDownload = { onStartDownload(model) },
                                onCancelDownload = { onCancelDownload(model.id) },
                                onActivate = {
                                    val downloadedInfo = downloadedModels.firstOrNull {
                                        it.fileName.contains(model.id, ignoreCase = true) ||
                                        it.fileName.equals(model.fileName, ignoreCase = true)
                                    }
                                    if (downloadedInfo != null) {
                                        onSelectDownloadedModel(downloadedInfo)
                                    } else {
                                        onSelectPreset(model.toModelPreset())
                                    }
                                },
                                onDelete = {
                                    onDeleteDownloadedModel(model.fileName)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSpaceCard(storageInfo: StorageSpaceInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Depolama Alanı",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "${storageInfo.freeGbFormatted} Boş / ${storageInfo.totalGbFormatted}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { storageInfo.usedPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (storageInfo.usedPercentage > 0.9f) ErrorRed else ElectricBlue,
                trackColor = OledBorder
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GGUF Modelleri Boyutu: ${storageInfo.modelsMbFormatted}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Yerel On-Device",
                    fontSize = 11.sp,
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CustomDownloadPromptCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(OledSurfaceElevated, OledSurfaceCard)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(CyanPrimary.copy(alpha = 0.5f), NeonViolet.copy(alpha = 0.3f))),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Özel GGUF URL'si İle İndir",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Hugging Face veya doğrudan .gguf indirme bağlantısı yapıştır",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DownloadableModelCard(
    model: DownloadableModel,
    task: DownloadTask?,
    isDownloaded: Boolean,
    isCurrentlyLoaded: Boolean,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val isDownloading = task?.status == DownloadStatus.DOWNLOADING || task?.status == DownloadStatus.CONNECTING

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrentlyLoaded) OledSurfaceVariant else OledSurfaceCard)
            .border(
                1.dp,
                if (isCurrentlyLoaded) CyanPrimary else if (isDownloading) ElectricBlue else OledBorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            // Top row: Title + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentlyLoaded) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = model.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentlyLoaded) CyanPrimary else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(OledBackground)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = model.quantization,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonViolet.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = model.parameterCount,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonViolet
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Author and Category
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.author,
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "•", fontSize = 11.sp, color = TextMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = model.category.displayName,
                    fontSize = 11.sp,
                    color = ElectricBlue,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = model.description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Hardware Specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "~${model.expectedTps} t/s",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyanPrimary
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${model.estimatedRamMb} MB RAM",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }

                if (model.supportsReasoning) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonViolet.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🧠 CoT Reasoning",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonViolet
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Download Status & Actions
            if (isDownloading && task != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OledBackground)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (task.status == DownloadStatus.CONNECTING) "Sunucuya bağlanıyor..." else "İndiriliyor: ${task.progressPercent}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )

                        Text(
                            text = "${task.downloadedFormatted} / ${task.totalFormatted}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyanPrimary,
                        trackColor = OledBorder
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hız: ${task.speedFormatted} • Kalan: ${task.etaFormatted}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )

                        TextButton(
                            onClick = onCancelDownload,
                            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                        ) {
                            Text(text = "İptal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (isDownloaded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cihaza İndirildi (${model.fileSizeFormatted})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Sil",
                                tint = ErrorRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = onActivate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrentlyLoaded) OledSurfaceElevated else CyanPrimary,
                                contentColor = if (isCurrentlyLoaded) TextSecondary else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isCurrentlyLoaded
                        ) {
                            Text(
                                text = if (isCurrentlyLoaded) "Aktif" else "Yükle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "İndirme Boyutu: ${model.fileSizeFormatted}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )

                    Button(
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "İndir",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedModelCard(
    info: DownloadedModelInfo,
    isCurrentlyLoaded: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentlyLoaded) OledSurfaceVariant else OledSurfaceCard)
            .border(
                1.dp,
                if (isCurrentlyLoaded) CyanPrimary else OledBorderSubtle,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentlyLoaded) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = info.matchingPreset?.name ?: info.matchingDownloadable?.name ?: info.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentlyLoaded) CyanPrimary else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${info.fileSizeFormatted} • GGUF Dosyası",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = ErrorRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyLoaded) OledSurfaceElevated else CyanPrimary,
                        contentColor = if (isCurrentlyLoaded) TextSecondary else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCurrentlyLoaded
                ) {
                    Text(
                        text = if (isCurrentlyLoaded) "Aktif" else "Yükle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCustomGgufCard(
    onSelectFile: () -> Unit,
    onOpenUrlDialog: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(CyanPrimary, ElectricBlue)),
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = OledSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Harici GGUF Modeli İçe Aktar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Cihazındaki İndirilenler klasöründen herhangi bir .gguf dosyasını doğrudan seç veya bağlantı yapıştır.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectFile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dosya Seç",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onOpenUrlDialog,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyanPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(CyanPrimary, ElectricBlue))
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "URL ile İndir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomModelDownloadDialog(
    onDismiss: () -> Unit,
    onDownload: (name: String, url: String) -> Unit
) {
    var modelName by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Özel GGUF İndir",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hugging Face resolve linki veya doğrudan bir .gguf indirme URL'si girin.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Adı (İsteğe bağlı)", fontSize = 12.sp) },
                    placeholder = { Text("örn. Llama 3.2 1B Custom", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = OledBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = {
                        downloadUrl = it
                        isError = false
                    },
                    label = { Text("GGUF İndirme URL'si", fontSize = 12.sp) },
                    placeholder = { Text("https://huggingface.co/.../model-q4_k_m.gguf", fontSize = 12.sp) },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = OledBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (isError) {
                    Text(
                        text = "Lütfen geçerli bir HTTP / HTTPS indirme bağlantısı girin.",
                        color = ErrorRed,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (downloadUrl.isBlank() || (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://"))) {
                        isError = true
                    } else {
                        val name = modelName.ifBlank {
                            downloadUrl.substringAfterLast("/").removeSuffix(".gguf")
                        }
                        onDownload(name, downloadUrl.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("İndirmeyi Başlat", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) CyanPrimary.copy(alpha = 0.2f) else OledSurfaceCard)
            .border(
                1.dp,
                if (isSelected) CyanPrimary else OledBorderSubtle,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) CyanPrimary else TextSecondary
        )
    }
}

@Composable
private fun LoadingProgressCard(loadingState: ModelLoadingState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanPrimary, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Model Yükleniyor...",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary
                )
                Text(
                    text = "${(loadingState.progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { loadingState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyanPrimary,
                trackColor = OledBorder
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = loadingState.stepDescription.ifEmpty { "Model ağırlıkları belleğe aktarılıyor..." },
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ActiveModelCard(metadata: GgufMetadata, preset: ModelPreset?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OledBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AKTİF MODEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = metadata.quantization,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = metadata.modelName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = metadata.fileName,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Grid Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetaPill(label = "Mimari", value = metadata.architecture)
                MetaPill(label = "Bağlam (Ctx)", value = "${metadata.contextLength}")
                MetaPill(label = "Boyut / RAM", value = "${metadata.memoryEstimateMb} MB")
                MetaPill(label = "Tensörler", value = "${metadata.tensorCount}")
            }
        }
    }
}

@Composable
private fun MetaPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(OledSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 9.sp, color = TextMuted)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun DeviceRamStatusCard(memory: DeviceMemoryStatus, modelRamMb: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OledBorderSubtle, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cihaz Bellek Durumu (RAM)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "${memory.availableRamMb} MB Boş / ${memory.totalRamMb} MB",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (memory.isLowMemory) ErrorRed else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (memory.usedPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (memory.usedPercentage > 85) ErrorRed else if (memory.usedPercentage > 70) WarningAmber else ElectricBlue,
                trackColor = OledBorder
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Model ayak izi: ~$modelRamMb MB • Çevrimdışı ve güvenli on-device bellek tahsisi",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun PresetModelCard(
    preset: ModelPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) OledSurfaceVariant else OledSurfaceCard)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CyanPrimary else OledBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = preset.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CyanPrimary else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(OledBackground)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = preset.quantization,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = preset.description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "~${preset.expectedTps} t/s",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${preset.estimatedRamMb} MB RAM",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }

                if (preset.supportsReasoning) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonViolet.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🧠 Reasoning",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonViolet
                        )
                    }
                }
            }
        }
    }
}
