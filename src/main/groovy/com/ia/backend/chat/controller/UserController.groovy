package com.ia.backend.chat.controller

import com.ia.backend.chat.dto.CreateUserRequest
import com.ia.backend.chat.dto.UserResponse
import com.ia.backend.chat.service.UserService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping('/v1/users')
class UserController {

	private final UserService userService

	UserController(UserService userService) {
		this.userService = userService
	}

	@PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
	ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		def created = userService.create(request)
		def location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path('/{id}')
				.buildAndExpand(created.id)
				.toUri()
		ResponseEntity.created(location).body(created)
	}

	/**
	 * Borra el usuario. Las conversations y messages asociados desaparecen
	 * por la ON DELETE CASCADE de la base de datos.
	 */
	@DeleteMapping(path = ['/{id}', '/{id}/'])
	ResponseEntity<Void> delete(@PathVariable('id') UUID id) {
		userService.delete(id)
		ResponseEntity.noContent().build()
	}
}
