package com.ia.backend.chat.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

import java.time.Duration

/**
 * Cliente REST de la API externa de crawling.
 *
 *     POST {EXTERNAL_API_CRAWLER}
 *     Authorization: Bearer {EXTERNAL_API_CRAWLER_AUTH}
 *     Content-Type: application/json
 *     {"url": "https://ejemplo.com/pagina"}
 *
 * Devuelve el contenido de la url convertido a markdown (CrawlResponse).
 * Es un cliente de uso interno: NO expone ningún endpoint, los futuros flujos
 * lo inyectarán como bean.
 */
@Component
class CrawlerClient {

	private static final log = LoggerFactory.getLogger(CrawlerClient)

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5)
	// Algunas páginas tardan hasta ~20 s en rastrearse: se deja el mismo margen
	// como límite máximo de espera por la respuesta
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(20)

	private final String baseUrl
	private final String authToken
	private final RestClient restClient

	CrawlerClient(@Value('${external.api.crawler:}') String baseUrl,
				   @Value('${external.api.crawler.auth:}') String authToken) {
		this.baseUrl = baseUrl?.trim()
		this.authToken = authToken?.trim()
		def requestFactory = new SimpleClientHttpRequestFactory()
		requestFactory.connectTimeout = CONNECT_TIMEOUT
		requestFactory.readTimeout = READ_TIMEOUT
		this.restClient = RestClient.builder().requestFactory(requestFactory).build()
	}

	/**
	 * Rastrea una url y devuelve su contenido en markdown.
	 *
	 * @param url url a rastrear (http o https)
	 * @return la respuesta ya mapeada; nunca null
	 * @throws CrawlerClientException si falta la configuración, la url es inválida,
	 *                                la API devuelve un error HTTP o no hay conexión
	 */
	CrawlResponse crawl(String url) {
		if (!baseUrl) {
			throw new CrawlerClientException('La variable de entorno EXTERNAL_API_CRAWLER no está configurada')
		}
		if (!authToken) {
			throw new CrawlerClientException('La variable de entorno EXTERNAL_API_CRAWLER_AUTH no está configurada')
		}

		def normalized = normalizeUrl(url)

		try {
			def response = restClient.post()
					.uri(baseUrl)
					.header(HttpHeaders.AUTHORIZATION, 'Bearer ' + authToken)
					.body(new CrawlRequest(url: normalized))
					.retrieve()
					.body(CrawlResponse)
			return response ?: new CrawlResponse(url: normalized, success: false)
		} catch (RestClientResponseException e) {
			log.warn('La API externa de crawling respondió HTTP {} para [{}]', e.statusCode.value(), normalized)
			throw new CrawlerClientException("La API externa de crawling respondió HTTP ${e.statusCode.value()}", e)
		} catch (ResourceAccessException e) {
			log.error('Sin conexión con la API externa de crawling: {}', e.message)
			throw new CrawlerClientException('No se pudo contactar con la API externa de crawling', e)
		}
	}

	private static String normalizeUrl(String url) {
		def normalized = url?.trim()
		if (!normalized) {
			throw new CrawlerClientException('La url a rastrear no puede estar vacía')
		}

		URI parsed
		try {
			parsed = URI.create(normalized)
		} catch (IllegalArgumentException e) {
			throw new CrawlerClientException("La url no es válida: ${normalized}", e)
		}

		if (!(parsed.scheme in ['http', 'https']) || !parsed.host) {
			throw new CrawlerClientException("La url debe ser http(s) y tener host: ${normalized}")
		}

		normalized
	}
}
