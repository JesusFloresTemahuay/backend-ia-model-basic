package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Un resultado de la API externa de búsqueda.
 * Los campos que llegan en snake_case se mapean explícitamente con @JsonProperty.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class SearchResult {

	String template
	String title
	String content

	@JsonProperty('img_src')
	String imgSrc

	@JsonProperty('iframe_src')
	String iframeSrc

	@JsonProperty('audio_src')
	String audioSrc

	String thumbnail

	@JsonProperty('publishedDate')
	String publishedDate

	String pubdate
	String length
	String views
	String author
	String metadata
	String priority

	List<String> engines = []

	@JsonProperty('open_group')
	Boolean openGroup

	@JsonProperty('close_group')
	Boolean closeGroup

	List<Integer> positions = []

	Double score
	String category
	String url
	String engine

	@JsonProperty('parsed_url')
	List<String> parsedUrl = []
}
