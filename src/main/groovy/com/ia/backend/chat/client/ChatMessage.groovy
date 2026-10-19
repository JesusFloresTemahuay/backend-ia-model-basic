package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Un mensaje de la conversación enviada al modelo: {"role": "...", "content": "..."}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ChatMessage {

	String role
	String content
}
