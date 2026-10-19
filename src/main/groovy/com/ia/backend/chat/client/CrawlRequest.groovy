package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Body del POST a la API externa de crawling: la url a rastrear.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class CrawlRequest {

	String url
}
