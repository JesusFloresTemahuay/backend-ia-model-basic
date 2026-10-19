package com.ia.backend.chat.service

import com.ia.backend.chat.client.ChatMessage
import com.ia.backend.chat.client.CompletionRequest
import com.ia.backend.chat.client.CompletionResponse
import com.ia.backend.chat.client.CompletionsClient
import com.ia.backend.chat.client.CompletionsClientException
import com.ia.backend.chat.dto.ChatRequest
import com.ia.backend.chat.entity.Message
import com.ia.backend.chat.repository.ConversationRepository
import org.springframework.stereotype.Service

/**
 * Flujo de chat de /v1/chats:
 *
 *  1. Valida que la conversación exista y pertenezca al user_id recibido.
 *  2. Persiste el message entrante (sólo role y content del payload; position
 *     automática y model = model del payload).
 *  3. Consulta el historial completo de la conversación ordenado por position
 *     ascendente y lo remapea a sólo role y content.
 *  4. Llama al cliente de completions con el model, temperature y stream del
 *     payload y el historial remapeado.
 *  5. Persiste la respuesta del modelo como un message más de la misma
 *     conversación: role y content del choice, y reasoning en la columna
 *     message, para que el historial de las siguientes llamadas la incluya.
 *
 * Sin @Transactional global a propósito: la llamada al modelo puede tardar hasta
 * 5 minutos y no debe mantener ocupada una conexión de la base de datos. Cada
 * acceso a datos ocurre en la transacción corta de su propio servicio.
 */
@Service
class WorkflowService {

	private final MessageService messageService
	private final ConversationRepository conversationRepository
	private final CompletionsClient completionsClient

	WorkflowService(MessageService messageService,
					ConversationRepository conversationRepository,
					CompletionsClient completionsClient) {
		this.messageService = messageService
		this.conversationRepository = conversationRepository
		this.completionsClient = completionsClient
	}

	CompletionResponse chat(ChatRequest request) {
		validarConversacion(request)

		// 1. Persiste el message entrante (position automática, model = payload)
		messageService.create(
				request.conversationId,
				request.message.role,
				request.message.content,
				request.model)

		// 2. Historial ordenado por position, remapeado a sólo role y content
		def historial = messageService.findByConversation(request.conversationId).collect { m ->
			new ChatMessage(role: m.role, content: m.content)
		}

		// 4. Llamada síncrona al modelo (hasta 5 minutos)
		def respuesta = completionsClient.complete(new CompletionRequest(
				model: request.model,
				messages: historial,
				temperature: request.temperature,
				stream: request.stream))

		// 5. Persiste la respuesta del modelo en la misma conversación
		guardarRespuestaDelAsistente(request, respuesta)

		respuesta
	}

	/**
	 * Guarda la respuesta del modelo como un message de la conversación:
	 * role y content del primer choice, y reasoning en la columna message
	 * (si no trae reasoning se guarda content para no violar el NOT NULL).
	 */
	private void guardarRespuestaDelAsistente(ChatRequest request, CompletionResponse respuesta) {
		def choice = respuesta?.choices ? respuesta.choices[0] : null
		if (choice?.message == null) {
			throw new CompletionsClientException('La API externa de completions devolvió una respuesta sin choices')
		}

		def delModelo = choice.message
		def content = delModelo.content
		def reasoning = delModelo.reasoning

		if (!content?.trim() && !reasoning?.trim()) {
			throw new CompletionsClientException(
					'La API externa de completions devolvió un choice sin content ni reasoning')
		}

		messageService.create(new Message(
				conversationId: request.conversationId,
				role: delModelo.role?.trim() ?: 'assistant',
				content: content?.trim() ?: reasoning,
				message: reasoning ?: content,
				model: request.model))
	}

	private void validarConversacion(ChatRequest request) {
		def conversation = conversationRepository.findById(request.conversationId)
				.orElseThrow { new ConversationNotFoundException(request.conversationId) }

		if (conversation.userId != request.userId) {
			throw new ConversationAccessDeniedException(request.conversationId, request.userId)
		}
	}
}
