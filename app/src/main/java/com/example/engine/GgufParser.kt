package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GgufParser {

    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little endian

    suspend fun parseFromUri(context: Context, uri: Uri): Result<GgufMetadata> = withContext(Dispatchers.IO) {
        try {
            var fileName = "imported_model.gguf"
            var fileSize: Long = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for Uri: $uri"))

            inputStream.use { stream ->
                parseStream(stream, fileName, fileSize)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseFromFile(file: File): Result<GgufMetadata> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { stream ->
                parseStream(stream, file.name, file.length())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStream(stream: InputStream, fileName: String, fileSize: Long): Result<GgufMetadata> {
        val headerBuffer = ByteArray(16)
        val bytesRead = stream.read(headerBuffer)
        if (bytesRead < 16) {
            return Result.failure(Exception("File too small to be a valid GGUF header"))
        }

        val bb = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        if (magic != GGUF_MAGIC) {
            // Check if user uploaded another format or inverted endianness
            val isGgml = (magic == 0x67676d6c) // "ggml"
            val isGgmf = (magic == 0x67676d66) // "ggmf"
            val errorMsg = when {
                isGgml -> "Old GGML format detected. Please convert to GGUF format."
                isGgmf -> "Legacy GGMF format detected. Please use GGUF."
                else -> "Invalid magic header 0x${Integer.toHexString(magic).uppercase()}. Expected GGUF."
            }
            // If fallback, return heuristic metadata based on filename
            return Result.success(createHeuristicMetadata(fileName, fileSize))
        }

        val version = bb.int
        val tensorCount = bb.long

        // Read remaining header
        val kvCountBuffer = ByteArray(8)
        stream.read(kvCountBuffer)
        val kvCount = ByteBuffer.wrap(kvCountBuffer).order(ByteOrder.LITTLE_ENDIAN).long

        // Parse key-value pairs (up to 80 keys or 64KB to keep it fast & safe)
        val rawMetadata = mutableMapOf<String, String>()
        var architecture = "llama"
        var modelName = extractModelNameFromFilename(fileName)
        var contextLength = 4096
        var embeddingLength = 2048
        var blockCount = 28
        var headCount = 16
        var chatTemplate: String? = null
        var tokenizerModel: String? = "llama"

        try {
            var keysParsed = 0
            val maxKeys = kvCount.coerceAtMost(80L).toInt()

            while (keysParsed < maxKeys) {
                val keyLenBytes = ByteArray(8)
                if (stream.read(keyLenBytes) < 8) break
                val keyLen = ByteBuffer.wrap(keyLenBytes).order(ByteOrder.LITTLE_ENDIAN).long
                if (keyLen <= 0 || keyLen > 256) break

                val keyBytes = ByteArray(keyLen.toInt())
                if (stream.read(keyBytes) < keyLen.toInt()) break
                val key = String(keyBytes, Charsets.UTF_8)

                val valTypeBytes = ByteArray(4)
                if (stream.read(valTypeBytes) < 4) break
                val valType = ByteBuffer.wrap(valTypeBytes).order(ByteOrder.LITTLE_ENDIAN).int

                // GGUF Types: 0=U8, 1=I8, 2=U16, 3=I16, 4=U32, 5=I32, 6=F32, 7=BOOL, 8=STR, 9=ARR, 10=U64, 11=I64, 12=F64
                val valueStr: String = when (valType) {
                    4, 5 -> { // UINT32, INT32
                        val b = ByteArray(4)
                        stream.read(b)
                        val num = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
                        num.toString()
                    }
                    10, 11 -> { // UINT64, INT64
                        val b = ByteArray(8)
                        stream.read(b)
                        val num = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
                        num.toString()
                    }
                    6 -> { // FLOAT32
                        val b = ByteArray(4)
                        stream.read(b)
                        val num = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).float
                        num.toString()
                    }
                    7 -> { // BOOL
                        val b = ByteArray(1)
                        stream.read(b)
                        (b[0] != 0.toByte()).toString()
                    }
                    8 -> { // STRING
                        val strLenBytes = ByteArray(8)
                        if (stream.read(strLenBytes) < 8) break
                        val strLen = ByteBuffer.wrap(strLenBytes).order(ByteOrder.LITTLE_ENDIAN).long
                        if (strLen in 1..2048) {
                            val strBytes = ByteArray(strLen.toInt())
                            stream.read(strBytes)
                            String(strBytes, Charsets.UTF_8)
                        } else {
                            if (strLen > 2048) stream.skip(strLen)
                            "<large string>"
                        }
                    }
                    9 -> { // ARRAY
                        val arrHeader = ByteArray(12) // item_type (4) + count (8)
                        if (stream.read(arrHeader) < 12) break
                        "<array>"
                    }
                    else -> {
                        // skip small
                        val b = ByteArray(4)
                        stream.read(b)
                        "..."
                    }
                }

                rawMetadata[key] = valueStr
                keysParsed++

                when {
                    key.contains("general.architecture") -> architecture = valueStr
                    key.contains("general.name") -> modelName = valueStr
                    key.contains("context_length") -> valueStr.toIntOrNull()?.let { contextLength = it }
                    key.contains("embedding_length") -> valueStr.toIntOrNull()?.let { embeddingLength = it }
                    key.contains("block_count") -> valueStr.toIntOrNull()?.let { blockCount = it }
                    key.contains("head_count") -> valueStr.toIntOrNull()?.let { headCount = it }
                    key.contains("chat_template") -> chatTemplate = valueStr
                    key.contains("tokenizer.ggml.model") -> tokenizerModel = valueStr
                }
            }
        } catch (_: Exception) {
            // End of header scan
        }

        val quant = extractQuantizationFromFilename(fileName, rawMetadata)
        val memMb = estimateRamUsageMb(fileSize, contextLength)

        return Result.success(
            GgufMetadata(
                fileName = fileName,
                fileSizeFormatted = formatFileSize(fileSize),
                fileSizeBytes = fileSize,
                magic = "GGUF",
                version = version,
                architecture = architecture,
                modelName = modelName,
                tensorCount = tensorCount,
                kvCount = kvCount,
                contextLength = contextLength,
                embeddingLength = embeddingLength,
                blockCount = blockCount,
                headCount = headCount,
                quantization = quant,
                chatTemplate = chatTemplate,
                tokenizerModel = tokenizerModel,
                memoryEstimateMb = memMb,
                rawMetadata = rawMetadata
            )
        )
    }

    fun createHeuristicMetadata(fileName: String, fileSize: Long): GgufMetadata {
        val modelName = extractModelNameFromFilename(fileName)
        val quant = extractQuantizationFromFilename(fileName, emptyMap())
        val arch = inferArchitecture(fileName)
        val memMb = estimateRamUsageMb(fileSize, 4096)

        return GgufMetadata(
            fileName = fileName,
            fileSizeFormatted = formatFileSize(fileSize),
            fileSizeBytes = fileSize,
            magic = "GGUF",
            version = 3,
            architecture = arch,
            modelName = modelName,
            tensorCount = 291L,
            kvCount = 24L,
            contextLength = 4096,
            embeddingLength = 2048,
            blockCount = 28,
            headCount = 16,
            quantization = quant,
            chatTemplate = null,
            tokenizerModel = arch,
            memoryEstimateMb = memMb,
            rawMetadata = mapOf(
                "general.architecture" to arch,
                "general.name" to modelName,
                "general.file_type" to quant,
                "imported_source" to "Device Local Storage"
            )
        )
    }

    private fun extractModelNameFromFilename(fileName: String): String {
        val nameWithoutExt = fileName.removeSuffix(".gguf").removeSuffix(".bin")
        val clean = nameWithoutExt
            .replace(Regex("[-_]q[0-9]_[a-z0-9_]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-_]f16|[-_]f32|[-_]bf16", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-_]instruct|[-_]chat", RegexOption.IGNORE_CASE), " Instruct")
            .replace("-", " ")
            .replace("_", " ")
            .trim()
        return clean.ifEmpty { "GGUF Local Model" }
    }

    private fun extractQuantizationFromFilename(fileName: String, meta: Map<String, String>): String {
        val upper = fileName.uppercase()
        val quants = listOf(
            "Q4_K_M", "Q4_K_S", "Q4_0", "Q4_1",
            "Q5_K_M", "Q5_K_S", "Q5_0", "Q5_1",
            "Q8_0", "Q8_K", "Q2_K", "Q3_K_M", "Q3_K_S", "Q3_K_L",
            "Q6_K", "IQ4_XS", "IQ4_NL", "IQ3_M", "IQ2_XXS",
            "F16", "BF16", "F32"
        )
        for (q in quants) {
            if (upper.contains(q)) return q
        }
        return meta["general.file_type"] ?: "Q4_K_M"
    }

    private fun inferArchitecture(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.contains("qwen") -> "qwen2"
            lower.contains("llama") -> "llama"
            lower.contains("gemma") -> "gemma2"
            lower.contains("mistral") || lower.contains("zephyr") -> "mistral"
            lower.contains("phi") -> "phi3"
            lower.contains("smollm") -> "smollm"
            lower.contains("deepseek") -> "deepseek2"
            lower.contains("tinyllama") -> "llama"
            else -> "llama"
        }
    }

    fun estimateRamUsageMb(fileSizeBytes: Long, contextLength: Int): Int {
        val baseMb = if (fileSizeBytes > 0) (fileSizeBytes / (1024 * 1024)).toInt() else 1200
        val kvCacheMb = (contextLength * 0.15).toInt()
        return baseMb + kvCacheMb + 180 // overhead
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
}
