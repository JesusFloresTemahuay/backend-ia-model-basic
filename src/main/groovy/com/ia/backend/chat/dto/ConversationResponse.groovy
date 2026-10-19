package com.ia.backend.chat.dto

import com.ia.backend.chat.entity.Conversation

import java.time.Instant

/**
 * Respuesta pública de una conversación.
 */
class ConversationResponse {

	UUID id
	UUID userId
	String title
	Instant createdAt
	Instant updatedAt

	static ConversationResponse from(Conversation conversation) {
		new ConversationResponse(
				id: conversation.id,
				userId: conversation.userId,
				title: conversation.title,
				createdAt: conversation.createdAt,
				updatedAt: conversation.updatedAt
		)
	}
}
