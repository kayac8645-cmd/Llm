package com.example.engine

import com.example.data.local.ChatMessageEntity

object PromptTemplateFormatter {

    fun format(
        templateType: PromptTemplateType,
        systemPrompt: String,
        messages: List<ChatMessageEntity>,
        architecture: String = "llama"
    ): String {
        val resolvedType = if (templateType == PromptTemplateType.AUTO) {
            inferTemplateFromArchitecture(architecture)
        } else {
            templateType
        }

        val sb = StringBuilder()

        when (resolvedType) {
            PromptTemplateType.CHATML -> {
                // <|im_start|>system\n{system_prompt}<|im_end|>\n
                if (systemPrompt.isNotBlank()) {
                    sb.append("<|im_start|>system\n").append(systemPrompt.trim()).append("<|im_end|>\n")
                }
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "assistant" else "user"
                    sb.append("<|im_start|>").append(role).append("\n")
                        .append(msg.content.trim()).append("<|im_end|>\n")
                }
                sb.append("<|im_start|>assistant\n")
            }

            PromptTemplateType.LLAMA3 -> {
                // <|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n{system_prompt}<|eot_id|>
                sb.append("<|begin_of_text|>")
                if (systemPrompt.isNotBlank()) {
                    sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                        .append(systemPrompt.trim()).append("<|eot_id|>")
                }
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "assistant" else "user"
                    sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                        .append(msg.content.trim()).append("<|eot_id|>")
                }
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }

            PromptTemplateType.GEMMA -> {
                // <start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n
                if (systemPrompt.isNotBlank()) {
                    sb.append("<start_of_turn>user\nSystem Instruction: ").append(systemPrompt.trim()).append("<end_of_turn>\n")
                }
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "model" else "user"
                    sb.append("<start_of_turn>").append(role).append("\n")
                        .append(msg.content.trim()).append("<end_of_turn>\n")
                }
                sb.append("<start_of_turn>model\n")
            }

            PromptTemplateType.MISTRAL -> {
                // <s>[INST] {system_prompt}\n\n{user_message} [/INST] {assistant_response}</s>
                sb.append("<s>")
                var isFirst = true
                for (msg in messages) {
                    if (msg.role == "user") {
                        sb.append("[INST] ")
                        if (isFirst && systemPrompt.isNotBlank()) {
                            sb.append(systemPrompt.trim()).append("\n\n")
                            isFirst = false
                        }
                        sb.append(msg.content.trim()).append(" [/INST]")
                    } else {
                        sb.append(" ").append(msg.content.trim()).append("</s>")
                    }
                }
            }

            PromptTemplateType.DEEPSEEK -> {
                if (systemPrompt.isNotBlank()) {
                    sb.append("System: ").append(systemPrompt.trim()).append("\n\n")
                }
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "Assistant: " else "User: "
                    sb.append(role).append(msg.content.trim()).append("\n\n")
                }
                sb.append("Assistant: <think>\n")
            }

            PromptTemplateType.PHI3 -> {
                if (systemPrompt.isNotBlank()) {
                    sb.append("<|system|>\n").append(systemPrompt.trim()).append("<|end|>\n")
                }
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "<|assistant|>\n" else "<|user|>\n"
                    sb.append(role).append(msg.content.trim()).append("<|end|>\n")
                }
                sb.append("<|assistant|>\n")
            }

            PromptTemplateType.ALPACA -> {
                if (systemPrompt.isNotBlank()) {
                    sb.append(systemPrompt.trim()).append("\n\n")
                }
                for (msg in messages) {
                    if (msg.role == "user") {
                        sb.append("### Instruction:\n").append(msg.content.trim()).append("\n\n")
                    } else {
                        sb.append("### Response:\n").append(msg.content.trim()).append("\n\n")
                    }
                }
                sb.append("### Response:\n")
            }

            PromptTemplateType.RAW, PromptTemplateType.AUTO -> {
                if (systemPrompt.isNotBlank()) {
                    sb.append("System: ").append(systemPrompt.trim()).append("\n\n")
                }
                for (msg in messages) {
                    val role = if (msg.role == "assistant") "Assistant: " else "User: "
                    sb.append(role).append(msg.content.trim()).append("\n\n")
                }
                sb.append("Assistant: ")
            }
        }

        return sb.toString()
    }

    private fun inferTemplateFromArchitecture(arch: String): PromptTemplateType {
        val lower = arch.lowercase()
        return when {
            lower.contains("qwen") || lower.contains("smollm") || lower.contains("yi") -> PromptTemplateType.CHATML
            lower.contains("llama-3") || lower.contains("llama3") || lower.contains("llama_3") -> PromptTemplateType.LLAMA3
            lower.contains("gemma") -> PromptTemplateType.GEMMA
            lower.contains("mistral") || lower.contains("zephyr") -> PromptTemplateType.MISTRAL
            lower.contains("deepseek") -> PromptTemplateType.DEEPSEEK
            lower.contains("phi") -> PromptTemplateType.PHI3
            else -> PromptTemplateType.CHATML
        }
    }
}
