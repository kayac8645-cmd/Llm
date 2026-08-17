package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelName: String = "AURA-Local-Engine",
    val systemPrompt: String = "You are AURA, an ultra-fast on-device AI assistant running locally via GGUF.",
    val isPinned: Boolean = false
)
