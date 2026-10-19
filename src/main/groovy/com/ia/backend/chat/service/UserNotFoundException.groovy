package com.ia.backend.chat.service

import java.util.UUID

/** El usuario referenciado no existe. */
class UserNotFoundException extends RuntimeException {

	UserNotFoundException(UUID userId) {
		super("Usuario no encontrado: $userId")
	}
}
