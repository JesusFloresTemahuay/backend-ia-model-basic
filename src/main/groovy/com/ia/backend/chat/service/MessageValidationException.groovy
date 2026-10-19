package com.ia.backend.chat.service

/** Datos de un message inválidos (role no permitido, content vacío, etc.). */
class MessageValidationException extends RuntimeException {

	MessageValidationException(String message) {
		super(message)
	}
}
