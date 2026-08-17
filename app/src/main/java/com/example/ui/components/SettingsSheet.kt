package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AuraSettings
import com.example.engine.PromptTemplateType
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sheetState: SheetState,
    settings: AuraSettings,
    onUpdateSettings: (AuraSettings) -> Unit,
    onResetSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var contextSize by remember(settings) { mutableStateOf(settings.contextSize.toFloat()) }
    var temperature by remember(settings) { mutableStateOf(settings.temperature) }
    var topP by remember(settings) { mutableStateOf(settings.topP) }
    var topK by remember(settings) { mutableStateOf(settings.topK.toFloat()) }
    var repeatPenalty by remember(settings) { mutableStateOf(settings.repeatPenalty) }
    var gpuLayers by remember(settings) { mutableStateOf(settings.gpuLayers.toFloat()) }
    var cpuThreads by remember(settings) { mutableStateOf(settings.cpuThreads.toFloat()) }
    var systemPrompt by remember(settings) { mutableStateOf(settings.systemPrompt) }
    var promptTemplate by remember(settings) { mutableStateOf(settings.promptTemplate) }
    var showSpeed by remember(settings) { mutableStateOf(settings.showTokenSpeed) }

    var templateMenuExpanded by remember { mutableStateOf(false) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "İnference & Model Ayarları",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    TextButton(
                        onClick = {
                            onResetSettings()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sıfırla", color = CyanPrimary, fontSize = 12.sp)
                    }
                }
            }

            // GPU Layers Setting
            item {
                SettingSliderCard(
                    title = "GPU / Vulkan Katmanları (n_gpu_layers)",
                    description = "Katmanları mobil GPU (Adreno / Mali) birimine aktararak CPU yükünü azaltır.",
                    valueText = if (gpuLayers.toInt() == 0) "0 (Saf CPU Modu)" else "${gpuLayers.toInt()} Katman (GPU Hızlandırma)",
                    value = gpuLayers,
                    onValueChange = {
                        gpuLayers = it
                        onUpdateSettings(settings.copy(gpuLayers = it.toInt()))
                    },
                    valueRange = 0f..33f,
                    steps = 32
                )
            }

            // Context Window Size (n_ctx) Setting
            item {
                SettingSliderCard(
                    title = "Bağlam Penceresi Boyutu (n_ctx)",
                    description = "Modelin aynı anda hatırlayabileceği maksimum token sayısı (KV Cache bellek tüketimini etkiler).",
                    valueText = "${contextSize.toInt()} Tokens (~${(contextSize * 0.15).toInt()} MB RAM)",
                    value = contextSize,
                    onValueChange = {
                        contextSize = it
                        onUpdateSettings(settings.copy(contextSize = it.toInt()))
                    },
                    valueRange = 512f..8192f,
                    steps = 14
                )
            }

            // CPU Threads Setting
            item {
                SettingSliderCard(
                    title = "CPU İş Parçacığı Sayısı (n_threads)",
                    description = "Paralel matris çarpımı için kullanılacak ARM CPU çekirdeği sayısı.",
                    valueText = "${cpuThreads.toInt()} Threads",
                    value = cpuThreads,
                    onValueChange = {
                        cpuThreads = it
                        onUpdateSettings(settings.copy(cpuThreads = it.toInt()))
                    },
                    valueRange = 1f..8f,
                    steps = 6
                )
            }

            // Temperature Setting
            item {
                SettingSliderCard(
                    title = "Sıcaklık (Temperature)",
                    description = "Düşük değerler daha tutarlı ve kesin yanıtlar üretirken, yüksek değerler yaratıcılığı artırır.",
                    valueText = String.format(Locale.US, "%.2f", temperature),
                    value = temperature,
                    onValueChange = {
                        temperature = it
                        onUpdateSettings(settings.copy(temperature = it))
                    },
                    valueRange = 0.0f..1.5f
                )
            }

            // Top-P (Nucleus Sampling) Setting
            item {
                SettingSliderCard(
                    title = "Top-P (Nucleus Sampling)",
                    description = "Yalnızca kümülatif olasılığı P değerini aşan token havuzundan seçim yapar.",
                    valueText = String.format(Locale.US, "%.2f", topP),
                    value = topP,
                    onValueChange = {
                        topP = it
                        onUpdateSettings(settings.copy(topP = it))
                    },
                    valueRange = 0.1f..1.0f
                )
            }

            // Top-K Setting
            item {
                SettingSliderCard(
                    title = "Top-K",
                    description = "En yüksek olasılıklı ilk K adet token arasından örnekleme yapar.",
                    valueText = "${topK.toInt()}",
                    value = topK,
                    onValueChange = {
                        topK = it
                        onUpdateSettings(settings.copy(topK = it.toInt()))
                    },
                    valueRange = 1f..100f
                )
            }

            // Repeat Penalty Setting
            item {
                SettingSliderCard(
                    title = "Tekrar Cezası (Repeat Penalty)",
                    description = "Modelin aynı kelime ve cümleleri tekrarlamasını engeller.",
                    valueText = String.format(Locale.US, "%.2f", repeatPenalty),
                    value = repeatPenalty,
                    onValueChange = {
                        repeatPenalty = it
                        onUpdateSettings(settings.copy(repeatPenalty = it))
                    },
                    valueRange = 1.0f..1.5f
                )
            }

            // Prompt Template Selector
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Prompt Şablonu (Chat Template)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Modelin diyalog formatlama stili (ChatML, Llama-3, Gemma, Mistral)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OledSurface)
                                    .border(1.dp, OledBorder, RoundedCornerShape(8.dp))
                                    .clickable { templateMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = promptTemplate.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanPrimary
                                        )
                                        Text(
                                            text = promptTemplate.description,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = templateMenuExpanded,
                                onDismissRequest = { templateMenuExpanded = false },
                                modifier = Modifier.background(OledSurfaceElevated)
                            ) {
                                PromptTemplateType.values().forEach { t ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(text = t.displayName, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                Text(text = t.description, fontSize = 10.sp, color = TextMuted)
                                            }
                                        },
                                        onClick = {
                                            promptTemplate = t
                                            onUpdateSettings(settings.copy(promptTemplate = t))
                                            templateMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // System Prompt Editor
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Varsayılan Sistem İstemi (System Prompt)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Modele temel kimlik ve davranış talimatı verir.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = {
                                systemPrompt = it
                                onUpdateSettings(settings.copy(systemPrompt = it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = OledSurface,
                                unfocusedContainerColor = OledSurface,
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = OledBorder
                            )
                        )
                    }
                }
            }

            // Show Token Speed Switch
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OledBorderSubtle, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = OledSurfaceCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Token Hızını Göster (t/s Metrikleri)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Mesajların altında saniyede üretilen token miktarını gösterir.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Switch(
                            checked = showSpeed,
                            onCheckedChange = {
                                showSpeed = it
                                onUpdateSettings(settings.copy(showTokenSpeed = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = CyanPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = OledBorder
                            )
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingSliderCard(
    title: String,
    description: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
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
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = valueText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = OledBorder
                )
            )
        }
    }
}
