package com.ia.backend.chat.controller

import com.ia.backend.chat.dto.ConversationResponse
import com.ia.backend.chat.dto.CreateConversationRequest
import com.ia.backend.chat.service.ConversationService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping('/v1/conversations')
class ConversationController {

	private final ConversationService conversationService

	ConversationController(ConversationService conversationService) {
		this.conversationService = conversationService
	}

	// Se acepta con y sin barra final: /v1/conversations y /v1/conversations/
	@PostMapping(path = ['', '/'], consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
	ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request) {
		def created = conversationService.create(request)
		def location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path('/v1/conversations/{id}')
				.buildAndExpand(created.id)
				.toUri()
		ResponseEntity.created(location).body(created)
	}
}
