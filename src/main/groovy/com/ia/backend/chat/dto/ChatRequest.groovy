package com.ia.backend.chat.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.ia.backend.chat.client.ChatMessage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * Payload de POST /v1/chats:
 *
 * {
 *   "model": "un_modelo",
 *   "conversation_id": "un id",
 *   "user_id": "un id",
 *   "message": {"role": "system", "content": "..."},
 *   "temperature": 0.7,
 *   "stream": false
 * }
 *
 * model, temperature y stream son parametrizables en cada llamada.
 */
class ChatRequest {

	@NotBlank(message = 'el modelo es obligatorio')
	String model

	@NotNull(message = 'el conversationId es obligatorio')
	@JsonProperty('conversation_id')
	UUID conversationId

	@NotNull(message = 'el userId es obligatorio')
	@JsonProperty('user_id')
	UUID userId

	@NotNull(message = 'el message es obligatorio')
	ChatMessage message

	Double temperature

	Boolean stream
}
