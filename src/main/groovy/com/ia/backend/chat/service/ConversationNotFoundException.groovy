package com.ia.backend.chat.service

import java.util.UUID

/** La conversación referenciada no existe. */
class ConversationNotFoundException extends RuntimeException {

	ConversationNotFoundException(UUID conversationId) {
		super("Conversación no encontrada: $conversationId")
	}
}
