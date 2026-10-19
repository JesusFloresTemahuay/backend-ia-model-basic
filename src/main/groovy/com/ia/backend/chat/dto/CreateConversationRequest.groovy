package com.ia.backend.chat.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * Datos de entrada para la creación de una conversación.
 */
class CreateConversationRequest {

	@NotNull(message = 'el userId es obligatorio')
	UUID userId

	@Size(max = 250, message = 'el título no puede superar los 250 caracteres')
	String title
}
