package com.ia.backend.chat

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

/**
 * Verifica que el borrado en cascada de la base de datos real (ON DELETE CASCADE)
 * se reproduce igual en el esquema de test:
 *
 *   users.id -> conversations.user_id  (ON DELETE CASCADE)
 *   conversations.id -> messages.conversation_id  (ON DELETE CASCADE)
 */
@SpringBootTest
class CascadeDeleteTests {

	@Autowired
	UserRepository userRepository

	@Autowired
	ConversationRepository conversationRepository

	@Autowired
	MessageRepository messageRepository

	@Test
	void 'borrar un usuario elimina en cascada sus conversations y messages'() {
		def user = crearUsuario()
		def conversation = crearConversacion(user.id)
		def message = crearMensaje(conversation.id)
		messageRepository.flush()
		assertThat(messageRepository.countByConversationId(conversation.id)).isEqualTo(1L)

		userRepository.deleteById(user.id)

		assertThat(userRepository.existsById(user.id)).isFalse()
		assertThat(conversationRepository.existsById(conversation.id)).isFalse()
		assertThat(messageRepository.existsById(message.id)).isFalse()
	}

	@Test
	void 'borrar una conversacion elimina en cascada sus messages pero conserva el usuario'() {
		def user = crearUsuario()
		def conversation = crearConversacion(user.id)
		def message = crearMensaje(conversation.id)
		messageRepository.flush()

		conversationRepository.deleteById(conversation.id)

		assertThat(conversationRepository.existsById(conversation.id)).isFalse()
		assertThat(messageRepository.existsById(message.id)).isFalse()
		assertThat(userRepository.existsById(user.id)).isTrue()
	}

	@Test
	void 'borrar un message no afecta a la conversacion ni al usuario'() {
		def user = crearUsuario()
		def conversation = crearConversacion(user.id)
		def message = crearMensaje(conversation.id)
		messageRepository.flush()

		messageRepository.deleteById(message.id)

		assertThat(messageRepository.existsById(message.id)).isFalse()
		assertThat(conversationRepository.existsById(conversation.id)).isTrue()
		assertThat(userRepository.existsById(user.id)).isTrue()
	}

	private User crearUsuario() {
		userRepository.save(new User(
				email: "cascade-${UUID.randomUUID()}@example.com",
				passwordHash: '$2a$10$abcdefghijklmnopqrstuvwxyz'))
	}

	private Conversation crearConversacion(UUID userId) {
		conversationRepository.save(new Conversation(userId: userId, title: 'Cascada'))
	}

	private Message crearMensaje(UUID conversationId) {
		messageRepository.save(new Message(
				conversationId: conversationId,
				position: 0L,
				role: 'user',
				content: 'hola',
				message: 'hola'))
	}
}
