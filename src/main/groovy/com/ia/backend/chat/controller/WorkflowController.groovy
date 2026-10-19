package com.ia.backend.chat.controller

import com.ia.backend.chat.client.CompletionResponse
import com.ia.backend.chat.dto.ChatRequest
import com.ia.backend.chat.service.WorkflowService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Flujo de chat: POST /v1/chats
 *
 * El orquestamiento vive en WorkflowService; aquí sólo se traduce HTTP <-> dominio.
 * Respuesta 200 con la completación del modelo (formato chat.completion).
 */
@RestController
@RequestMapping('/v1/chats')
class WorkflowController {

	private final WorkflowService workflowService

	WorkflowController(WorkflowService workflowService) {
		this.workflowService = workflowService
	}

	@PostMapping(path = ['', '/'], consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
	ResponseEntity<CompletionResponse> chat(@Valid @RequestBody ChatRequest request) {
		ResponseEntity.ok(workflowService.chat(request))
	}
}
