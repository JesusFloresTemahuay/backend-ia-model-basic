package com.ia.backend.chat.service

import java.util.UUID

/** El message solicitado no existe. */
class MessageNotFoundException extends RuntimeException {

	MessageNotFoundException(UUID messageId) {
		super("Message no encontrado: $messageId")
	}
}
