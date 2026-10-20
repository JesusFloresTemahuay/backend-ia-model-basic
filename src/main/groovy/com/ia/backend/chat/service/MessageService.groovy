package com.ia.backend.chat.service

import com.ia.backend.chat.entity.Message
import com.ia.backend.chat.repository.ConversationRepository
import com.ia.backend.chat.repository.MessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Servicio de la entidad Message. Sin endpoints ni controllers: pensado para ser
 * invocado desde servicios y flujos del backend (por ejemplo, al guardar la entrada
 * del usuario y la respuesta del modelo antes/tras llamar a los clientes externos).
 */
@Service
class MessageService {

	/**
	 * Debe coincidir con el CHECK constraint role de la tabla messages.
	 * Los datos que inyecta una herramienta se guardan con role 'tool' y el
	 * nombre de la herramienta en metadata (p. ej. {"tool": "internet-tool"}).
	 */
	private static final Set<String> ALLOWED_ROLES = ['system', 'user', 'assistant', 'tool'].toSet()

	private final MessageRepository messageRepository
	private final ConversationRepository conversationRepository

	MessageService(MessageRepository messageRepository, ConversationRepository conversationRepository) {
		this.messageRepository = messageRepository
		this.conversationRepository = conversationRepository
	}

	/**
	 * Guarda un message completo. Si no trae position se le asigna la siguiente
	 * (max + 1) de forma serializada por conversación; si no trae message se
	 * copia desde content.
	 *
	 * @throws MessageValidationException si el role no está permitido o el content está vacío
	 * @throws ConversationNotFoundException si la conversación no existe
	 */
	@Transactional
	Message create(Message message) {
		validar(message)

		// Bloquea la conversación (FOR UPDATE) para que dos inserciones simultáneas
		// no compitan por el mismo position (unique conversation_id + position)
		def conversation = conversationRepository.findByIdForUpdate(message.conversationId)
				.orElseThrow { new ConversationNotFoundException(message.conversationId) }

		if (message.position == null) {
			def max = messageRepository.findMaxPosition(conversation.id)
			message.position = max == null ? 0L : max + 1L
		}

		if (message.message == null) {
			message.message = message.content
		}

		messageRepository.saveAndFlush(message)
	}

	/**
	 * Atajo para los flujos de chat: crea un message con message = content
	 * y sin model ni metadata.
	 *
	 * @param model opcional (por ejemplo, el modelo que generó la respuesta)
	 */
	@Transactional
	Message create(UUID conversationId, String role, String content, String model = null) {
		create(new Message(
				conversationId: conversationId,
				role: role,
				content: content,
				message: content,
				model: model))
	}

	/** Messages de la conversación ordenados por position; vacío si no existe. */
	@Transactional(readOnly = true)
	List<Message> findByConversation(UUID conversationId) {
		messageRepository.findByConversationIdOrderByPositionAsc(conversationId)
	}

	/** Número de messages de la conversación. */
	@Transactional(readOnly = true)
	long countByConversation(UUID conversationId) {
		messageRepository.countByConversationId(conversationId)
	}

	/** Devuelve el message o lanza MessageNotFoundException. */
	@Transactional(readOnly = true)
	Message require(UUID messageId) {
		messageRepository.findById(messageId)
				.orElseThrow { new MessageNotFoundException(messageId) }
	}

	/** Elimina el message; si no existe lanza MessageNotFoundException. */
	@Transactional
	void delete(UUID messageId) {
		def message = messageRepository.findById(messageId)
				.orElseThrow { new MessageNotFoundException(messageId) }
		messageRepository.delete(message)
	}

	private static void validar(Message message) {
		if (message == null) {
			throw new MessageValidationException('El message no puede ser nulo')
		}
		if (message.conversationId == null) {
			throw new MessageValidationException('El conversationId es obligatorio')
		}
		if (!message.role?.trim() || !ALLOWED_ROLES.contains(message.role)) {
			throw new MessageValidationException(
					"El role debe ser uno de ${ALLOWED_ROLES.sort().join(', ')}")
		}
		if (!message.content?.trim()) {
			throw new MessageValidationException('El content no puede estar vacío')
		}
	}
}
