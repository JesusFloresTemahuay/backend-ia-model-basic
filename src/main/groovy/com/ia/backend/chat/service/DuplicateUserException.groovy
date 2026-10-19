package com.ia.backend.chat.service

/** El email ya está registrado (violación de la restricción única users_email_key). */
class DuplicateUserException extends RuntimeException {

	DuplicateUserException(String email) {
		super("El email ya está registrado: $email")
	}
}
