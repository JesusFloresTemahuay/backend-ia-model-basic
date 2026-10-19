package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Respuesta de la API externa de búsqueda:
 * POST {EXTERNAL_API_SEARCHER}?q={texto}&format=json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class SearchResponse {

	String query

	List<SearchResult> results = []

	List<Object> answers = []
	List<Object> corrections = []
	List<Object> infoboxes = []

	List<String> suggestions = []

	@JsonProperty('unresponsive_engines')
	List<List<String>> unresponsiveEngines = []
}
