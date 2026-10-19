package com.ia.backend.chat.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder

import java.time.Duration

/**
 * Cliente REST de la API externa de búsqueda.
 *
 *     POST {EXTERNAL_API_SEARCHER}?q={texto}&format=json
 *
 * La URL se toma de la variable de entorno EXTERNAL_API_SEARCHER (propiedad
 * external.api.searcher). Es un cliente de uso interno: NO expone ningún endpoint,
 * los futuros servicios lo inyectarán como bean.
 */
@Component
class SearcherClient {

	private static final log = LoggerFactory.getLogger(SearcherClient)

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5)
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30)

	private final String baseUrl
	private final RestClient restClient

	SearcherClient(@Value('${external.api.searcher:}') String baseUrl) {
		this.baseUrl = baseUrl?.trim()
		def requestFactory = new SimpleClientHttpRequestFactory()
		requestFactory.connectTimeout = CONNECT_TIMEOUT
		requestFactory.readTimeout = READ_TIMEOUT
		this.restClient = RestClient.builder().requestFactory(requestFactory).build()
	}

	/**
	 * Ejecuta una búsqueda contra la API externa.
	 *
	 * @param query texto a buscar (parámetro q)
	 * @return la respuesta ya mapeada; nunca null
	 * @throws SearcherClientException si la consulta está vacía, falta la configuración,
	 *                                 la API devuelve un error HTTP o no hay conexión
	 */
	SearchResponse search(String query) {
		if (!baseUrl) {
			throw new SearcherClientException('La variable de entorno EXTERNAL_API_SEARCHER no está configurada')
		}

		def normalized = query?.trim()
		if (!normalized) {
			throw new SearcherClientException('La consulta (q) no puede estar vacía')
		}

		def uri = UriComponentsBuilder.fromUriString(baseUrl)
				.queryParam('q', normalized)
				.queryParam('format', 'json')
				.build()
				.encode()
				.toUri()

		try {
			def response = restClient.post().uri(uri).retrieve().body(SearchResponse)
			return response ?: new SearchResponse(query: normalized, results: [])
		} catch (RestClientResponseException e) {
			log.warn('La API externa de búsqueda respondió HTTP {} para [{}]', e.statusCode.value(), normalized)
			throw new SearcherClientException("La API externa de búsqueda respondió HTTP ${e.statusCode.value()}", e)
		} catch (ResourceAccessException e) {
			log.error('Sin conexión con la API externa de búsqueda: {}', e.message)
			throw new SearcherClientException('No se pudo contactar con la API externa de búsqueda', e)
		}
	}
}
