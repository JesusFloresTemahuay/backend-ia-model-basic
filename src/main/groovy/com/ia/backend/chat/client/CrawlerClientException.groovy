package com.ia.backend.chat.client

/** Fallo al invocar la API externa de crawling (HTTP, red o configuración). */
class CrawlerClientException extends RuntimeException {

	CrawlerClientException(String message) {
		super(message)
	}

	CrawlerClientException(String message, Throwable cause) {
		super(message, cause)
	}
}
