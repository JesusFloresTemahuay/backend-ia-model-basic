package com.ia.backend.chat.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Body del POST a la API externa de completions:
 *
 * {
 *   "model": "qwen3.8-4b-q4",
 *   "messages": [ {"role": "...", "content": "..."}, ... ],
 *   "temperature": 0.7,
 *   "stream": false
 * }
 *
 * Todos los campos son parametrizables por el servicio que lo invoca.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class CompletionRequest {

	/** Identificador del modelo a usar (obligatorio). */
	String model

	/** Historial de la conversación, de uno a n elementos. */
	List<ChatMessage> messages = []

	/** Temperatura de muestreo; si no se indica se usa 0.7. */
	Double temperature

	/** Si se desea respuesta en streaming; por defecto false. */
	Boolean stream
}
