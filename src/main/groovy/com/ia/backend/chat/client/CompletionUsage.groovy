package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** Consumo de tokens de la llamada (usage). */
@JsonIgnoreProperties(ignoreUnknown = true)
class CompletionUsage {

	@JsonProperty('prompt_tokens')
	Integer promptTokens

	@JsonProperty('completion_tokens')
	Integer completionTokens

	@JsonProperty('total_tokens')
	Integer totalTokens
}
