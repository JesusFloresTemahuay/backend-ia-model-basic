package com.ia.backend.chat.client

/** Fallo al invocar la API externa de completions (HTTP, red o configuración). */
class CompletionsClientException extends RuntimeException {

	CompletionsClientException(String message) {
		super(message)
	}

	CompletionsClientException(String message, Throwable cause) {
		super(message, cause)
	}
}
