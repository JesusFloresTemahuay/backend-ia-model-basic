package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Respuesta de la API externa de completions (formato chat.completion).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class CompletionResponse {

	Long created
	String object
	String id
	String model

	List<CompletionChoice> choices = []

	CompletionUsage usage
}
