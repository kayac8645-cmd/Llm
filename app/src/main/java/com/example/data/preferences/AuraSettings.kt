package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.engine.PromptTemplateType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuraSettings(
    val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val gpuLayers: Int = 0,
    val cpuThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8),
    val systemPrompt: String = "You are AURA, an ultra-fast on-device AI assistant running locally via GGUF.",
    val promptTemplate: PromptTemplateType = PromptTemplateType.AUTO,
    val autoScroll: Boolean = true,
    val showTokenSpeed: Boolean = true,
    val vibrateOnComplete: Boolean = true
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_ai_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AuraSettings> = _settings.asStateFlow()

    private fun loadSettings(): AuraSettings {
        val defaultThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val templateName = prefs.getString("prompt_template", PromptTemplateType.AUTO.name) ?: PromptTemplateType.AUTO.name
        val template = try {
            PromptTemplateType.valueOf(templateName)
        } catch (_: Exception) {
            PromptTemplateType.AUTO
        }

        return AuraSettings(
            contextSize = prefs.getInt("context_size", 2048),
            temperature = prefs.getFloat("temperature", 0.7f),
            topP = prefs.getFloat("top_p", 0.9f),
            topK = prefs.getInt("top_k", 40),
            repeatPenalty = prefs.getFloat("repeat_penalty", 1.1f),
            gpuLayers = prefs.getInt("gpu_layers", 0),
            cpuThreads = prefs.getInt("cpu_threads", defaultThreads),
            systemPrompt = prefs.getString("system_prompt", "You are AURA, an ultra-fast on-device AI assistant running locally via GGUF.") ?: "",
            promptTemplate = template,
            autoScroll = prefs.getBoolean("auto_scroll", true),
            showTokenSpeed = prefs.getBoolean("show_token_speed", true),
            vibrateOnComplete = prefs.getBoolean("vibrate_on_complete", true)
        )
    }

    fun updateSettings(newSettings: AuraSettings) {
        prefs.edit()
            .putInt("context_size", newSettings.contextSize)
            .putFloat("temperature", newSettings.temperature)
            .putFloat("top_p", newSettings.topP)
            .putInt("top_k", newSettings.topK)
            .putFloat("repeat_penalty", newSettings.repeatPenalty)
            .putInt("gpu_layers", newSettings.gpuLayers)
            .putInt("cpu_threads", newSettings.cpuThreads)
            .putString("system_prompt", newSettings.systemPrompt)
            .putString("prompt_template", newSettings.promptTemplate.name)
            .putBoolean("auto_scroll", newSettings.autoScroll)
            .putBoolean("show_token_speed", newSettings.showTokenSpeed)
            .putBoolean("vibrate_on_complete", newSettings.vibrateOnComplete)
            .apply()

        _settings.value = newSettings
    }

    fun resetToDefaults() {
        val default = AuraSettings()
        updateSettings(default)
    }
}
