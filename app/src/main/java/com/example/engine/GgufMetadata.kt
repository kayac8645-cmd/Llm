package com.example.engine

enum class PromptTemplateType(val displayName: String, val description: String) {
    AUTO("Auto-Detect", "Automatically detect template from GGUF metadata"),
    CHATML("ChatML", "<|im_start|>user...<|im_end|> (Qwen, SmolLM, Yi)"),
    LLAMA3("Llama 3", "<|start_header_id|>user...<|end_header_id|> (Llama 3.x)"),
    GEMMA("Gemma", "<start_of_turn>user...<end_of_turn> (Gemma 2)"),
    MISTRAL("Mistral / Zephyr", "[INST] user [/INST] (Mistral, Mixtral)"),
    DEEPSEEK("DeepSeek", "User: ... Assistant: <think> (DeepSeek R1/V3)"),
    PHI3("Phi-3", "<|user|>...<|end|><|assistant|> (Phi-3 Mini)"),
    ALPACA("Alpaca", "### Instruction: ... ### Response:"),
    RAW("Raw Text", "Plain text without wrapper tags")
}

data class GgufMetadata(
    val fileName: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val magic: String = "GGUF",
    val version: Int = 3,
    val architecture: String = "llama",
    val modelName: String = "Unknown Model",
    val tensorCount: Long = 0,
    val kvCount: Long = 0,
    val contextLength: Int = 4096,
    val embeddingLength: Int = 2048,
    val blockCount: Int = 24,
    val headCount: Int = 16,
    val quantization: String = "Q4_K_M",
    val chatTemplate: String? = null,
    val tokenizerModel: String? = "llama",
    val memoryEstimateMb: Int = 1250,
    val rawMetadata: Map<String, String> = emptyMap()
)

data class ModelLoadingState(
    val status: Status = Status.UNLOADED,
    val progress: Float = 0f,
    val stepDescription: String = "",
    val errorMessage: String? = null
) {
    enum class Status {
        UNLOADED,
        PARSING_METADATA,
        ALLOCATING_MEMORY,
        MAPPING_TENSORS,
        COMPILING_GRAPH,
        LOADED,
        ERROR
    }
}

data class GenerationMetrics(
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val totalTokens: Int = 0,
    val timeToFirstTokenMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val tokensPerSecond: Float = 0f,
    val promptEvalTokensPerSecond: Float = 0f,
    val memoryPeakMb: Int = 0
)

data class GenerationToken(
    val token: String,
    val isFinished: Boolean = false,
    val metrics: GenerationMetrics? = null,
    val isThinking: Boolean = false
)
