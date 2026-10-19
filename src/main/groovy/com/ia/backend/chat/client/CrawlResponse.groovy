package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Respuesta de la API externa de crawling: el contenido de la url en markdown.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class CrawlResponse {

	String url
	String filter
	String query
	String cache
	String markdown
	Boolean success
}
