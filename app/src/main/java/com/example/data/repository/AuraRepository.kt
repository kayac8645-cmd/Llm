package com.example.data.repository

import com.example.data.local.AuraDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import kotlinx.coroutines.flow.Flow

class AuraRepository(private val database: AuraDatabase) {
    private val conversationDao = database.conversationDao()
    private val chatMessageDao = database.chatMessageDao()

    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForConversation(conversationId)
    }

    suspend fun getMessagesSnapshot(conversationId: Long): List<ChatMessageEntity> {
        return chatMessageDao.getMessagesSnapshot(conversationId)
    }

    suspend fun getConversationById(id: Long): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }

    suspend fun createConversation(
        title: String = "New Chat",
        modelName: String = "AURA-Local-Engine",
        systemPrompt: String = "You are AURA, an ultra-fast on-device AI assistant running locally via GGUF."
    ): Long {
        val conv = ConversationEntity(
            title = title,
            modelName = modelName,
            systemPrompt = systemPrompt,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return conversationDao.insertConversation(conv)
    }

    suspend fun updateConversationTitle(id: Long, newTitle: String) {
        conversationDao.updateTitle(id, newTitle)
    }

    suspend fun togglePinConversation(id: Long, isPinned: Boolean) {
        conversationDao.setPinned(id, isPinned)
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteConversationById(id)
    }

    suspend fun deleteAllConversations() {
        conversationDao.deleteAllConversations()
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        val messageId = chatMessageDao.insertMessage(message)
        // Also update the conversation's updatedAt timestamp
        val conv = conversationDao.getConversationById(message.conversationId)
        if (conv != null) {
            conversationDao.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
        }
        return messageId
    }

    suspend fun updateMessageContent(id: Long, content: String, tokensCount: Int, timeMs: Long, tps: Float) {
        chatMessageDao.updateMessageContent(id, content, tokensCount, timeMs, tps)
    }

    suspend fun deleteMessage(id: Long) {
        chatMessageDao.deleteMessageById(id)
    }
}
