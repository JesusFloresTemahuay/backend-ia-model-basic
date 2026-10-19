package com.ia.backend.chat.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

import java.time.Duration

/**
 * Cliente REST de la API externa de completions.
 *
 *     POST {EXTERNAL_API_COMPLETIONS}
 *     {
 *       "model": "...",
 *       "messages": [ {"role": "...", "content": "..."}, ... ],
 *       "temperature": 0.7,
 *       "stream": false
 *     }
 *
 * Devuelve la respuesta en formato chat.completion (CompletionResponse).
 * Es un cliente de uso interno: NO expone ningún endpoint, los futuros flujos
 * lo inyectarán como bean.
 */
@Component
class CompletionsClient {

	private static final log = LoggerFactory.getLogger(CompletionsClient)

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5)
	// Un modelo de razonamiento puede tardar bastante en generar la respuesta
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(120)

	private static final double DEFAULT_TEMPERATURE = 0.7d

	private final String baseUrl
	private final RestClient restClient

	CompletionsClient(@Value('${external.api.completions:}') String baseUrl) {
		this.baseUrl = baseUrl?.trim()
		def requestFactory = new SimpleClientHttpRequestFactory()
		requestFactory.connectTimeout = CONNECT_TIMEOUT
		requestFactory.readTimeout = READ_TIMEOUT
		this.restClient = RestClient.builder().requestFactory(requestFactory).build()
	}

	/**
	 * Envía la conversación al modelo y devuelve la completación.
	 *
	 * @param request petición con model, messages, temperature y stream parametrizables
	 * @return la respuesta ya mapeada; nunca null
	 * @throws CompletionsClientException si falta la configuración, la petición no es
	 *                                    válida, la API devuelve un error HTTP o no hay conexión
	 */
	CompletionResponse complete(CompletionRequest request) {
		if (!baseUrl) {
			throw new CompletionsClientException('La variable de entorno EXTERNAL_API_COMPLETIONS no está configurada')
		}

		def normalized = normalize(request)

		try {
			def response = restClient.post()
					.uri(baseUrl)
					.body(normalized)
					.retrieve()
					.body(CompletionResponse)
			return response ?: new CompletionResponse(model: normalized.model, choices: [])
		} catch (RestClientResponseException e) {
			log.warn('La API externa de completions respondió HTTP {} para el modelo [{}]', e.statusCode.value(), normalized.model)
			throw new CompletionsClientException("La API externa de completions respondió HTTP ${e.statusCode.value()}", e)
		} catch (ResourceAccessException e) {
			log.error('Sin conexión con la API externa de completions: {}', e.message)
			throw new CompletionsClientException('No se pudo contactar con la API externa de completions', e)
		}
	}

	/** Versión directa para una sola consulta: crea la petición con valores por defecto. */
	CompletionResponse complete(String model, String userContent) {
		complete(new CompletionRequest(
				model: model,
				messages: [new ChatMessage(role: 'user', content: userContent)]))
	}

	private static CompletionRequest normalize(CompletionRequest request) {
		if (request == null) {
			throw new CompletionsClientException('La petición de completions no puede ser nula')
		}
		if (!request.model?.trim()) {
			throw new CompletionsClientException('El modelo (model) es obligatorio')
		}

		def messages = request.messages ?: []
		messages.eachWithIndex { message, i ->
			if (message == null || !message.role?.trim() || !message.content?.trim()) {
				throw new CompletionsClientException("El message en la posición ${i} debe tener role y content")
			}
		}

		// Valores por defecto si el caller no los indicó
		if (request.temperature == null) {
			request.temperature = DEFAULT_TEMPERATURE
		}
		if (request.stream == null) {
			request.stream = false
		}

		request
	}
}
