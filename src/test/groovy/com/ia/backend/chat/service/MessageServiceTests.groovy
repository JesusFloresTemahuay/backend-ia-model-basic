package com.ia.backend.chat.service

import com.ia.backend.chat.entity.Conversation
import com.ia.backend.chat.entity.Message
import com.ia.backend.chat.entity.User
import com.ia.backend.chat.repository.ConversationRepository
import com.ia.backend.chat.repository.MessageRepository
import com.ia.backend.chat.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * Tests de MessageService (sin controllers: el servicio se usa desde flujos internos).
 */
@SpringBootTest
class MessageServiceTests {

	@Autowired
	MessageService messageService

	@Autowired
	UserRepository userRepository

	@Autowired
	ConversationRepository conversationRepository

	@Autowired
	MessageRepository messageRepository

	private UUID nuevaConversacion() {
		def user = userRepository.save(new User(
				email: "msg-${UUID.randomUUID()}@example.com", passwordHash: 'x'))
		conversationRepository.save(new Conversation(userId: user.id, title: 'Mensajes')).id
	}

	@Test
	void 'asigna positions consecutivos y completa los campos derivados'() {
		def conversationId = nuevaConversacion()

		def primero = messageService.create(conversationId, 'user', 'hola')
		def segundo = messageService.create(conversationId, 'assistant', '¿qué tal?', 'qwen3.8-4b-q4')

		assertThat(primero.position).isEqualTo(0L)
		assertThat(segundo.position).isEqualTo(1L)
		assertThat(primero.id).isNotNull()
		assertThat(primero.message).isEqualTo('hola')          // se copia desde content
		assertThat(primero.status).isEqualTo('completed')      // default de la entidad
		assertThat(primero.createdAt).isNotNull()
		assertThat(segundo.model).isEqualTo('qwen3.8-4b-q4')
		assertThat(primero.metadata).isEmpty()                 // jsonb por defecto {}
	}

	@Test
	void 'respeta la position explicita y sigue a partir de ella'() {
		def conversationId = nuevaConversacion()

		def explicito = messageService.create(new Message(
				conversationId: conversationId, role: 'user', content: 'hola', position: 10L))
		def siguiente = messageService.create(conversationId, 'assistant', 'ok')

		assertThat(explicito.position).isEqualTo(10L)
		assertThat(siguiente.position).isEqualTo(11L)
	}

	@Test
	void 'lista los messages de la conversacion ordenados por position'() {
		def conversationId = nuevaConversacion()
		messageService.create(conversationId, 'system', 'eres un asistente')
		messageService.create(conversationId, 'user', 'hola')
		messageService.create(conversationId, 'assistant', 'hola!')

		def messages = messageService.findByConversation(conversationId)

		assertThat(messages).extracting('role')
				.containsExactly('system', 'user', 'assistant')
		assertThat(messageService.countByConversation(conversationId)).isEqualTo(3L)
	}

	@Test
	void 'otra conversacion no ve los messages de la anterior'() {
		def uno = nuevaConversacion()
		def dos = nuevaConversacion()
		messageService.create(uno, 'user', 'de la primera')

		assertThat(messageService.findByConversation(dos)).isEmpty()
	}

	@Test
	void 'valida el role el content y la conversacion'() {
		def conversationId = nuevaConversacion()

		assertThatThrownBy { messageService.create(conversationId, 'banana', 'hola') }
				.isInstanceOf(MessageValidationException)
				.hasMessageContaining('role')
				.hasMessageContaining('assistant, system, tool, user')

		// Las herramientas se guardan con role 'tool' (+ metadata), no con un rol propio
		assertThatThrownBy { messageService.create(conversationId, 'internet-tool', 'hola') }
				.isInstanceOf(MessageValidationException)
				.hasMessageContaining('role')

		assertThatThrownBy { messageService.create(conversationId, 'user', '   ') }
				.isInstanceOf(MessageValidationException)
				.hasMessageContaining('content')

		assertThatThrownBy { messageService.create((Message) null) }
				.isInstanceOf(MessageValidationException)

		assertThatThrownBy { messageService.create(UUID.randomUUID(), 'user', 'hola') }
				.isInstanceOf(ConversationNotFoundException)
				.hasMessageContaining('Conversación no encontrada')
	}

	@Test
	void 'require devuelve el message o lanza MessageNotFoundException'() {
		def conversationId = nuevaConversacion()
		def guardado = messageService.create(conversationId, 'user', 'hola')

		assertThat(messageService.require(guardado.id).id).isEqualTo(guardado.id)

		assertThatThrownBy { messageService.require(UUID.randomUUID()) }
				.isInstanceOf(MessageNotFoundException)
				.hasMessageContaining('Message no encontrado')
	}

	@Test
	void 'delete elimina el message'() {
		def conversationId = nuevaConversacion()
		def guardado = messageService.create(conversationId, 'user', 'hola')

		messageService.delete(guardado.id)

		assertThat(messageRepository.existsById(guardado.id)).isFalse()
		assertThatThrownBy { messageService.delete(guardado.id) }
				.isInstanceOf(MessageNotFoundException)
	}
}
