package com.ia.backend.chat.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Datos de entrada para la creación de un usuario.
 */
class CreateUserRequest {

	@NotBlank(message = 'el email es obligatorio')
	@Email(message = 'el email no tiene un formato válido')
	String email

	@NotBlank(message = 'la contraseña es obligatoria')
	@Size(min = 8, max = 72, message = 'la contraseña debe tener entre 8 y 72 caracteres')
	String password

	@Size(max = 100, message = 'el nombre no puede superar los 100 caracteres')
	String displayName
}
