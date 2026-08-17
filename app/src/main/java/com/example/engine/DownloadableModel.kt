package com.example.engine

enum class ModelCategory(val displayName: String) {
    RECOMMENDED("Önerilen"),
    COMPACT("Hafif & Hızlı"),
    REASONING("Akıl Yürütme (CoT)"),
    CODING("Kodlama"),
    ADVANCED("Gelişmiş")
}

data class DownloadableModel(
    val id: String,
    val name: String,
    val author: String,
    val parameterCount: String,
    val quantization: String,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val downloadUrl: String,
    val huggingFaceRepo: String,
    val contextLength: Int,
    val estimatedRamMb: Int,
    val expectedTps: Float,
    val category: ModelCategory,
    val description: String,
    val promptTemplate: PromptTemplateType,
    val supportsReasoning: Boolean = false,
    val isRecommendedForLowEnd: Boolean = false
) {
    val fileName: String
        get() = "${id.lowercase()}-$quantization.gguf"

    fun toModelPreset(): ModelPreset {
        return ModelPreset(
            id = id,
            name = name,
            author = author,
            parameterCount = parameterCount,
            quantization = quantization,
            architecture = when {
                id.contains("qwen", ignoreCase = true) -> "qwen2"
                id.contains("phi", ignoreCase = true) -> "phi3"
                id.contains("gemma", ignoreCase = true) -> "gemma2"
                else -> "llama"
            },
            contextLength = contextLength,
            estimatedRamMb = estimatedRamMb,
            expectedTps = expectedTps,
            description = description,
            promptTemplate = promptTemplate,
            isRecommendedForLowEnd = isRecommendedForLowEnd,
            supportsReasoning = supportsReasoning
        )
    }

    fun toGgufMetadata(localFilePath: String? = null): GgufMetadata {
        val arch = when {
            id.contains("qwen", ignoreCase = true) -> "qwen2"
            id.contains("phi", ignoreCase = true) -> "phi3"
            id.contains("gemma", ignoreCase = true) -> "gemma2"
            else -> "llama"
        }
        return GgufMetadata(
            fileName = localFilePath ?: fileName,
            fileSizeFormatted = fileSizeFormatted,
            fileSizeBytes = fileSizeBytes,
            magic = "GGUF",
            version = 3,
            architecture = arch,
            modelName = name,
            tensorCount = when (parameterCount) {
                "135M" -> 118L
                "0.5B" -> 212L
                "1.1B" -> 291L
                "1.5B" -> 342L
                "2B" -> 420L
                else -> 480L
            },
            kvCount = 28L,
            contextLength = contextLength,
            embeddingLength = when (parameterCount) {
                "135M" -> 576
                "0.5B" -> 896
                "1.1B" -> 2048
                "1.5B" -> 1536
                "2B" -> 2304
                else -> 3072
            },
            blockCount = when (parameterCount) {
                "135M" -> 30
                "0.5B" -> 24
                "1.1B" -> 22
                "1.5B" -> 28
                "2B" -> 26
                else -> 32
            },
            headCount = 16,
            quantization = quantization,
            chatTemplate = null,
            tokenizerModel = arch,
            memoryEstimateMb = estimatedRamMb,
            rawMetadata = mapOf(
                "general.architecture" to arch,
                "general.name" to name,
                "general.author" to author,
                "general.file_type" to quantization,
                "general.parameter_count" to parameterCount,
                "huggingface.repo" to huggingFaceRepo
            )
        )
    }
}

object ModelDownloadCatalog {
    val DOWNLOAD_PRESETS = listOf(
        DownloadableModel(
            id = "Qwen2.5-0.5B-Instruct",
            name = "Qwen 2.5 0.5B Instruct",
            author = "Alibaba Cloud / Qwen",
            parameterCount = "0.49B",
            quantization = "Q4_K_M",
            fileSizeBytes = 398L * 1024 * 1024,
            fileSizeFormatted = "398 MB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            huggingFaceRepo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            contextLength = 32768,
            estimatedRamMb = 480,
            expectedTps = 44.5f,
            category = ModelCategory.RECOMMENDED,
            description = "PocketPal ve mobil cihazlar için en popüler süper hızlı model. Türkçe dahil çok dilli mükemmel yanıtlar üretir.",
            promptTemplate = PromptTemplateType.CHATML,
            isRecommendedForLowEnd = true
        ),
        DownloadableModel(
            id = "SmolLM2-135M-Instruct",
            name = "SmolLM2 135M Instruct",
            author = "Hugging Face",
            parameterCount = "135M",
            quantization = "Q8_0",
            fileSizeBytes = 145L * 1024 * 1024,
            fileSizeFormatted = "145 MB",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q8_0.gguf",
            huggingFaceRepo = "HuggingFaceTB/SmolLM2-135M-Instruct-GGUF",
            contextLength = 2048,
            estimatedRamMb = 180,
            expectedTps = 82.0f,
            category = ModelCategory.COMPACT,
            description = "Tüy kadar hafif! Düşük donanımlı eski telefonlarda bile ışık hızında token üretir, batarya dostudur.",
            promptTemplate = PromptTemplateType.CHATML,
            isRecommendedForLowEnd = true
        ),
        DownloadableModel(
            id = "DeepSeek-R1-Distill-1.5B",
            name = "DeepSeek R1 Distill Qwen 1.5B",
            author = "DeepSeek AI",
            parameterCount = "1.78B",
            quantization = "Q4_K_M",
            fileSizeBytes = 1120L * 1024 * 1024,
            fileSizeFormatted = "1.12 GB",
            downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            huggingFaceRepo = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            contextLength = 16384,
            estimatedRamMb = 1250,
            expectedTps = 24.0f,
            category = ModelCategory.REASONING,
            description = "Akıl yürütme (Chain-of-Thought) canavarı. Cevap vermeden önce <think> bloğu içinde adım adım düşünür.",
            promptTemplate = PromptTemplateType.DEEPSEEK,
            supportsReasoning = true
        ),
        DownloadableModel(
            id = "Llama-3.2-1B-Instruct",
            name = "Llama 3.2 1B Instruct",
            author = "Meta AI",
            parameterCount = "1.23B",
            quantization = "Q4_K_M",
            fileSizeBytes = 780L * 1024 * 1024,
            fileSizeFormatted = "780 MB",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            huggingFaceRepo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            contextLength = 8192,
            estimatedRamMb = 920,
            expectedTps = 29.5f,
            category = ModelCategory.RECOMMENDED,
            description = "Meta'nın son teknoloji mobil mimarisi. Üstün talimat takip yeteneği ve kompakt boyutta yüksek zeka.",
            promptTemplate = PromptTemplateType.LLAMA3
        ),
        DownloadableModel(
            id = "Qwen2.5-Coder-1.5B-Instruct",
            name = "Qwen 2.5 Coder 1.5B Instruct",
            author = "Alibaba Cloud / Qwen",
            parameterCount = "1.54B",
            quantization = "Q4_K_M",
            fileSizeBytes = 980L * 1024 * 1024,
            fileSizeFormatted = "980 MB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            huggingFaceRepo = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
            contextLength = 32768,
            estimatedRamMb = 1150,
            expectedTps = 25.8f,
            category = ModelCategory.CODING,
            description = "Mobil cihazda doğrudan kod yazma, hata ayıklama ve Python/Kotlin/C++ algoritmaları üretme uzmanı.",
            promptTemplate = PromptTemplateType.CHATML
        ),
        DownloadableModel(
            id = "Gemma-2-2B-IT",
            name = "Gemma 2 2B Instruct",
            author = "Google DeepMind",
            parameterCount = "2.61B",
            quantization = "Q4_K_M",
            fileSizeBytes = 1650L * 1024 * 1024,
            fileSizeFormatted = "1.65 GB",
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            huggingFaceRepo = "bartowski/gemma-2-2b-it-GGUF",
            contextLength = 8192,
            estimatedRamMb = 1680,
            expectedTps = 18.5f,
            category = ModelCategory.ADVANCED,
            description = "Google DeepMind'ın yüksek doğruluklu mobil modeli. Kapsamlı bilgi dağarcığı ve akıcı Türkçe.",
            promptTemplate = PromptTemplateType.GEMMA
        ),
        DownloadableModel(
            id = "TinyLlama-1.1B-Chat",
            name = "TinyLlama 1.1B Chat",
            author = "Zhang et al.",
            parameterCount = "1.10B",
            quantization = "Q4_K_M",
            fileSizeBytes = 669L * 1024 * 1024,
            fileSizeFormatted = "669 MB",
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            huggingFaceRepo = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
            contextLength = 2048,
            estimatedRamMb = 750,
            expectedTps = 33.0f,
            category = ModelCategory.COMPACT,
            description = "3 trilyon token ile eğitilmiş klasik kompakt model. Sohbet ve metin özetleme için güvenilir.",
            promptTemplate = PromptTemplateType.CHATML,
            isRecommendedForLowEnd = true
        ),
        DownloadableModel(
            id = "Phi-3.5-mini-Instruct",
            name = "Phi-3.5 Mini 3.8B Instruct",
            author = "Microsoft Research",
            parameterCount = "3.82B",
            quantization = "Q4_K_M",
            fileSizeBytes = 2240L * 1024 * 1024,
            fileSizeFormatted = "2.24 GB",
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            huggingFaceRepo = "bartowski/Phi-3.5-mini-instruct-GGUF",
            contextLength = 16384,
            estimatedRamMb = 2450,
            expectedTps = 13.0f,
            category = ModelCategory.ADVANCED,
            description = "Microsoft'un amiral gemisi küçük modeli. Ağır mantık, matematik ve karmaşık çok adımlı görevler.",
            promptTemplate = PromptTemplateType.PHI3
        )
    )
}
