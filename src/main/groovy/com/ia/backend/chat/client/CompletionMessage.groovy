package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Mensaje devuelto por el modelo dentro de cada choice. */
@JsonIgnoreProperties(ignoreUnknown = true)
class CompletionMessage {

	String role
	String content
	String reasoning
}
