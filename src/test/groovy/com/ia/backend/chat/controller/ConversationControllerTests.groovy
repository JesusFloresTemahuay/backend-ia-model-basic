package com.ia.backend.chat.controller

import com.ia.backend.chat.entity.User
import com.ia.backend.chat.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasSize
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ConversationControllerTests {

	@Autowired
	MockMvc mockMvc

	@Autowired
	UserRepository userRepository

	private UUID anyUser() {
		def saved = userRepository.save(new User(
				email: "conv-${UUID.randomUUID()}@example.com",
				passwordHash: '$2a$10$abcdefghijklmnopqrstuv'))
		saved.id
	}

	@Test
	void 'POST v1 conversations crea la conversacion y responde 201 con Location'() {
		String userId = anyUser().toString()

		mockMvc.perform(post('/v1/conversations')
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"userId":"$userId","title":"Mi conversación"}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string('Location', containsString('/v1/conversations/')))
				.andExpect(jsonPath('$.*', hasSize(5)))
				.andExpect(jsonPath('$.userId').value(userId))
				.andExpect(jsonPath('$.title').value('Mi conversación'))
				.andExpect(jsonPath('$.id').isNotEmpty())
				.andExpect(jsonPath('$.updatedAt').isNotEmpty())
	}

	@Test
	void 'POST v1 conversations con barra final tambien funciona'() {
		String userId = anyUser().toString()

		mockMvc.perform(post('/v1/conversations/')
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"userId":"$userId"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath('$.userId').value(userId))
				.andExpect(jsonPath('$.title').isEmpty())
	}

	@Test
	void 'POST v1 conversations devuelve 400 cuando falta el userId'() {
		mockMvc.perform(post('/v1/conversations')
				.contentType(MediaType.APPLICATION_JSON)
				.content('{"title":"Sin usuario"}'))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath('$.errors[0].field').value('userId'))
	}

	@Test
	void 'POST v1 conversations devuelve 404 cuando el usuario no existe'() {
		String userId = '00000000-0000-4000-8000-000000000000'

		mockMvc.perform(post('/v1/conversations')
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"userId":"$userId"}"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath('$.detail', containsString('Usuario no encontrado')))
	}
}
