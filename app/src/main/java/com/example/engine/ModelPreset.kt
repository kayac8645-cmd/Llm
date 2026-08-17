package com.example.engine

data class ModelPreset(
    val id: String,
    val name: String,
    val author: String,
    val parameterCount: String,
    val quantization: String,
    val architecture: String,
    val contextLength: Int,
    val estimatedRamMb: Int,
    val expectedTps: Float,
    val description: String,
    val promptTemplate: PromptTemplateType,
    val category: ModelCategory = ModelCategory.RECOMMENDED,
    val isRecommendedForLowEnd: Boolean = false,
    val supportsReasoning: Boolean = false
) {
    fun toGgufMetadata(): GgufMetadata {
        return GgufMetadata(
            fileName = "${id.lowercase()}-$quantization.gguf",
            fileSizeFormatted = "${(estimatedRamMb * 0.85).toInt()} MB",
            fileSizeBytes = (estimatedRamMb * 0.85 * 1024 * 1024).toLong(),
            magic = "GGUF",
            version = 3,
            architecture = architecture,
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
            tokenizerModel = architecture,
            memoryEstimateMb = estimatedRamMb,
            rawMetadata = mapOf(
                "general.architecture" to architecture,
                "general.name" to name,
                "general.author" to author,
                "general.file_type" to quantization,
                "general.parameter_count" to parameterCount,
                "general.category" to category.displayName
            )
        )
    }
}

object ModelCatalog {
    val PRESETS = listOf(
        ModelPreset(
            id = "Qwen2.5-0.5B-Instruct",
            name = "Qwen 2.5 0.5B Instruct",
            author = "Alibaba Cloud / Qwen",
            parameterCount = "0.49B",
            quantization = "Q4_K_M",
            architecture = "qwen2",
            contextLength = 32768,
            estimatedRamMb = 480,
            expectedTps = 42.5f,
            description = "Süper hızlı ve çok dilli (Türkçe destekli) kompakt model. Günlük asistanlık ve soru-cevap için ideal.",
            promptTemplate = PromptTemplateType.CHATML,
            category = ModelCategory.RECOMMENDED,
            isRecommendedForLowEnd = true
        ),
        ModelPreset(
            id = "SmolLM-135M-Instruct",
            name = "SmolLM 135M Instruct",
            author = "Hugging Face",
            parameterCount = "135M",
            quantization = "Q8_0",
            architecture = "llama",
            contextLength = 2048,
            estimatedRamMb = 210,
            expectedTps = 78.0f,
            description = "Tüy kadar hafif şampiyon. Tüm Android telefonlarda sıfır takılma ile anında token üretir, minimum pil harcar.",
            promptTemplate = PromptTemplateType.CHATML,
            category = ModelCategory.COMPACT,
            isRecommendedForLowEnd = true
        ),
        ModelPreset(
            id = "DeepSeek-R1-Distill-1.5B",
            name = "DeepSeek R1 Distill Qwen 1.5B",
            author = "DeepSeek AI",
            parameterCount = "1.78B",
            quantization = "Q4_K_M",
            architecture = "qwen2",
            contextLength = 16384,
            estimatedRamMb = 1250,
            expectedTps = 22.4f,
            description = "Akıl yürütme (Chain-of-Thought) canavarı. Cevap vermeden önce <think> bloğunda mantık analizi yapar.",
            promptTemplate = PromptTemplateType.DEEPSEEK,
            category = ModelCategory.REASONING,
            supportsReasoning = true
        ),
        ModelPreset(
            id = "Qwen2.5-Coder-1.5B-Instruct",
            name = "Qwen 2.5 Coder 1.5B Instruct",
            author = "Alibaba Cloud / Qwen",
            parameterCount = "1.54B",
            quantization = "Q4_K_M",
            architecture = "qwen2",
            contextLength = 32768,
            estimatedRamMb = 1150,
            expectedTps = 25.8f,
            description = "Kodlama, algoritma yazma, hata ayıklama ve refactoring için optimize edilmiş özel model.",
            promptTemplate = PromptTemplateType.CHATML,
            category = ModelCategory.CODING
        ),
        ModelPreset(
            id = "Llama-3.2-1B-Instruct",
            name = "Llama 3.2 1B Instruct",
            author = "Meta AI",
            parameterCount = "1.23B",
            quantization = "Q4_K_M",
            architecture = "llama",
            contextLength = 8192,
            estimatedRamMb = 920,
            expectedTps = 28.0f,
            description = "Meta'nın yeni nesil kompakt modeli. Üst düzey talimat anlama ve özet çıkarma kabiliyeti.",
            promptTemplate = PromptTemplateType.LLAMA3,
            category = ModelCategory.RECOMMENDED
        ),
        ModelPreset(
            id = "Gemma-2-2B-IT",
            name = "Gemma 2 2B Instruct",
            author = "Google DeepMind",
            parameterCount = "2.61B",
            quantization = "Q4_K_M",
            architecture = "gemma2",
            contextLength = 8192,
            estimatedRamMb = 1680,
            expectedTps = 18.2f,
            description = "Google mimarisiyle yüksek doğruluklu geniş bilgi dağarcığı ve akıcı Türkçe yanıtlar.",
            promptTemplate = PromptTemplateType.GEMMA,
            category = ModelCategory.ADVANCED
        ),
        ModelPreset(
            id = "TinyLlama-1.1B-Chat",
            name = "TinyLlama 1.1B Chat",
            author = "Zhang et al.",
            parameterCount = "1.10B",
            quantization = "Q4_K_M",
            architecture = "llama",
            contextLength = 2048,
            estimatedRamMb = 750,
            expectedTps = 32.0f,
            description = "3 trilyon token ile eğitilmiş hızlı ve stabil sohbet modeli.",
            promptTemplate = PromptTemplateType.CHATML,
            category = ModelCategory.COMPACT,
            isRecommendedForLowEnd = true
        ),
        ModelPreset(
            id = "Phi-3.5-mini-Instruct",
            name = "Phi-3.5 Mini 3.8B Instruct",
            author = "Microsoft Research",
            parameterCount = "3.82B",
            quantization = "Q4_K_M",
            architecture = "phi3",
            contextLength = 16384,
            estimatedRamMb = 2450,
            expectedTps = 12.8f,
            description = "Microsoft'un amiral gemisi küçük modeli. Ağır mantık, matematik ve karmaşık çok adımlı analiz.",
            promptTemplate = PromptTemplateType.PHI3,
            category = ModelCategory.ADVANCED
        )
    )

    fun getDefaultPreset(): ModelPreset = PRESETS[0]
}
