package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** Una alternativa de respuesta (choices[i]). */
@JsonIgnoreProperties(ignoreUnknown = true)
class CompletionChoice {

	Integer index

	@JsonProperty('finish_reason')
	String finishReason

	CompletionMessage message
}
