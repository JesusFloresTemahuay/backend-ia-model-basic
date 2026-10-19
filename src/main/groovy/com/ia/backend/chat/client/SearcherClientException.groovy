package com.ia.backend.chat.client

/** Fallo al invocar la API externa de búsqueda (HTTP, red o configuración). */
class SearcherClientException extends RuntimeException {

	SearcherClientException(String message) {
		super(message)
	}

	SearcherClientException(String message, Throwable cause) {
		super(message, cause)
	}
}
