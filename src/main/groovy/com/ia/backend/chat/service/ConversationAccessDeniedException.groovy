package com.ia.backend.chat.service

import java.util.UUID

/** La conversación no pertenece al usuario indicado en la petición. */
class ConversationAccessDeniedException extends RuntimeException {

	ConversationAccessDeniedException(UUID conversationId, UUID userId) {
		super("La conversación $conversationId no pertenece al usuario $userId")
	}
}
