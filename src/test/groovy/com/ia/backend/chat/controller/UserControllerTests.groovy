package com.ia.backend.chat.controller

import com.ia.backend.chat.entity.Conversation
import com.ia.backend.chat.entity.Message
import com.ia.backend.chat.entity.User
import com.ia.backend.chat.repository.ConversationRepository
import com.ia.backend.chat.repository.MessageRepository
import com.ia.backend.chat.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasItems
import static org.hamcrest.Matchers.hasSize
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTests {

	@Autowired
	MockMvc mockMvc

	@Autowired
	UserRepository userRepository

	@Autowired
	ConversationRepository conversationRepository

	@Autowired
	MessageRepository messageRepository

	@Test
	void 'POST v1 users crea el usuario y responde 201 con Location y sin passwordHash'() {
		String email = "ana-${UUID.randomUUID()}@example.com"

		def result = mockMvc.perform(post('/v1/users')
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"email":"$email","password":"secreta123","displayName":"Ana"}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string('Location', containsString('/v1/users/')))
				.andExpect(jsonPath('$.*', hasSize(4)))
				.andExpect(jsonPath('$.id').isNotEmpty())
				.andExpect(jsonPath('$.email').value(email))
				.andExpect(jsonPath('$.displayName').value('Ana'))
				.andExpect(jsonPath('$.createdAt').isNotEmpty())
				.andReturn()

		def id = UUID.fromString(result.response.getHeader('Location').tokenize('/').last())
		def stored = userRepository.findById(id).get()
		assert stored.email == email
		assert stored.passwordHash.startsWith('$2') // BCrypt, no texto plano
	}

	@Test
	void 'POST v1 users normaliza el email a minúsculas'() {
		String email = "carlos-${UUID.randomUUID()}@Example.com"

		mockMvc.perform(post('/v1/users')
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"email":"$email","password":"secreta123"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath('$.email').value(email.toLowerCase()))
	}

	@Test
	void 'POST v1 users devuelve 400 con los errores de campo cuando la validación falla'() {
		mockMvc.perform(post('/v1/users')
				.contentType(MediaType.APPLICATION_JSON)
				.content('{"email":"no-es-un-email","password":"123"}'))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath('$.title').value('Solicitud inválida'))
				.andExpect(jsonPath('$.errors', hasSize(2)))
				.andExpect(jsonPath('$.errors[*].field', hasItems('email', 'password')))
	}

	@Test
	void 'POST v1 users devuelve 400 cuando el cuerpo no es JSON válido'() {
		mockMvc.perform(post('/v1/users')
				.contentType(MediaType.APPLICATION_JSON)
				.content('esto no es json'))
				.andExpect(status().isBadRequest())
	}

	@Test
	void 'POST v1 users devuelve 409 cuando el email ya está registrado'() {
		String email = "duplicado-${UUID.randomUUID()}@example.com"
		String body = """{"email":"$email","password":"secreta123"}"""

		mockMvc.perform(post('/v1/users').contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())

		mockMvc.perform(post('/v1/users').contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath('$.detail', containsString('ya está registrado')))
	}

	@Test
	void 'DELETE v1 users devuelve 204 y borra en cascada conversations y messages'() {
		def user = userRepository.save(new User(
				email: "delete-${UUID.randomUUID()}@example.com", passwordHash: 'x'))
		def conversation = conversationRepository.save(new Conversation(userId: user.id, title: 'Bórrame'))
		def message = messageRepository.save(new Message(conversationId: conversation.id,
				position: 0L, role: 'user', content: 'hola', message: 'hola'))
		messageRepository.flush()

		mockMvc.perform(delete("/v1/users/${user.id}"))
				.andExpect(status().isNoContent())

		assert !userRepository.existsById(user.id)
		assert !conversationRepository.existsById(conversation.id)
		assert !messageRepository.existsById(message.id)
	}

	@Test
	void 'DELETE v1 users devuelve 404 cuando el usuario no existe'() {
		mockMvc.perform(delete('/v1/users/00000000-0000-4000-8000-000000000000'))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath('$.detail', containsString('Usuario no encontrado')))
	}

	@Test
	void 'DELETE v1 users devuelve 400 cuando el id no es un UUID valido'() {
		mockMvc.perform(delete('/v1/users/no-es-uuid'))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath('$.detail', containsString('no es válido')))
	}
}
