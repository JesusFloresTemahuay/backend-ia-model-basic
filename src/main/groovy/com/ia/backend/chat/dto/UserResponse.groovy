package com.ia.backend.chat.dto

import com.ia.backend.chat.entity.User

import java.time.Instant

/**
 * Respuesta pública de un usuario: nunca expone passwordHash.
 */
class UserResponse {

	UUID id
	String email
	String displayName
	Instant createdAt

	static UserResponse from(User user) {
		new UserResponse(
				id: user.id,
				email: user.email,
				displayName: user.displayName,
				createdAt: user.createdAt
		)
	}
}
