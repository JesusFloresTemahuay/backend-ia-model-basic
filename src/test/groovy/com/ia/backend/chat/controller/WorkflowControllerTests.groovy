package com.ia.backend.chat.controller

import com.ia.backend.chat.client.CompletionChoice
import com.ia.backend.chat.client.CompletionMessage
import com.ia.backend.chat.client.CompletionRequest
import com.ia.backend.chat.client.CompletionResponse
import com.ia.backend.chat.client.CompletionUsage
import com.ia.backend.chat.client.CompletionsClient
import com.ia.backend.chat.entity.Conversation
import com.ia.backend.chat.entity.User
import com.ia.backend.chat.repository.ConversationRepository
import com.ia.backend.chat.repository.UserRepository
import com.ia.backend.chat.service.MessageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc

import static org.assertj.core.api.Assertions.assertThat
import static org.hamcrest.Matchers.containsString
import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.never
import static org.mockito.Mockito.times
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Flujo completo de POST /v1/chats con el cliente de completions mockeado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkflowControllerTests {

	@Autowired
	MockMvc mockMvc

	@Autowired
	UserRepository userRepository

	@Autowired
	ConversationRepository conversationRepository

	@Autowired
	MessageService messageService

	@MockitoBean
	CompletionsClient completionsClient

	@BeforeEach
	void stubDelModelo() {
		when(completionsClient.complete(any(CompletionRequest))).thenReturn(new CompletionResponse(
				created: 1792403447L,
				object: 'chat.completion',
				id: 'respuesta-de-prueba',
				model: 'un_modelo',
				choices: [new CompletionChoice(index: 0, finishReason: 'stop',
						message: new CompletionMessage(role: 'assistant', content: 'respuesta del modelo',
								reasoning: 'un razonamiento'))],
				usage: new CompletionUsage(promptTokens: 10, completionTokens: 5, totalTokens: 15)))
	}

	private UUID nuevoUsuario() {
		userRepository.save(new User(
				email: "chat-${UUID.randomUUID()}@example.com", passwordHash: 'x')).id
	}

	private UUID nuevaConversacion(UUID userId) {
		conversationRepository.save(new Conversation(userId: userId, title: 'Chat')).id
	}

	private static String payload(String model, String conversationId, String userId,
								  String role, String content, String temperature, String stream) {
		"""{
			"model": "$model",
			"conversation_id": "$conversationId",
			"user_id": "$userId",
			"message": {"role": "$role", "content": "$content"},
			"temperature": $temperature,
			"stream": $stream
		}"""
	}

	@Test
	void 'POST v1 chats persiste el message consulta el historial y responde 200 con la completacion'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		// Historial previo a la llamada
		messageService.create(conversationId, 'user', 'primera pregunta')
		messageService.create(conversationId, 'assistant', 'primera respuesta', 'otro-modelo')

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'segunda pregunta', '0.3', 'false')))
				.andExpect(status().isOk())
				.andExpect(jsonPath('$.id').value('respuesta-de-prueba'))
				.andExpect(jsonPath('$.model').value('un_modelo'))
				.andExpect(jsonPath('$.choices[0].finish_reason').value('stop'))
				.andExpect(jsonPath('$.choices[0].message.content').value('respuesta del modelo'))
				.andExpect(jsonPath('$.usage.total_tokens').value(15))

		// El message entrante y la respuesta del modelo quedaron persistidos
		def guardados = messageService.findByConversation(conversationId)
		assertThat(guardados).hasSize(4)

		assertThat(guardados[2].position).isEqualTo(2L)
		assertThat(guardados[2].role).isEqualTo('user')
		assertThat(guardados[2].content).isEqualTo('segunda pregunta')
		assertThat(guardados[2].model).isEqualTo('un_modelo')

		assertThat(guardados[3].position).isEqualTo(3L)
		assertThat(guardados[3].role).isEqualTo('assistant')
		assertThat(guardados[3].content).isEqualTo('respuesta del modelo')
		assertThat(guardados[3].message).isEqualTo('un razonamiento')  // reasoning en la columna message
		assertThat(guardados[3].model).isEqualTo('un_modelo')

		// Lo que se envió al cliente: historial remapeado a role/content + parámetros del payload
		def captor = ArgumentCaptor.forClass(CompletionRequest)
		verify(completionsClient).complete(captor.capture())
		def enviado = captor.value
		assertThat(enviado.model).isEqualTo('un_modelo')
		assertThat(enviado.temperature).isEqualTo(0.3d)
		assertThat(enviado.stream).isFalse()
		assertThat(enviado.messages).extracting('role')
				.containsExactly('user', 'assistant', 'user')
		assertThat(enviado.messages).extracting('content')
				.containsExactly('primera pregunta', 'primera respuesta', 'segunda pregunta')
	}

	@Test
	void 'la respuesta del modelo queda en el historial de la siguiente llamada'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'primera pregunta', '0.7', 'false')))
				.andExpect(status().isOk())

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'segunda pregunta', '0.7', 'false')))
				.andExpect(status().isOk())

		def captor = ArgumentCaptor.forClass(CompletionRequest)
		verify(completionsClient, times(2)).complete(captor.capture())

		def segundaLlamada = captor.allValues[1]
		assertThat(segundaLlamada.messages).extracting('role')
				.containsExactly('user', 'assistant', 'user')
		assertThat(segundaLlamada.messages).extracting('content')
				.containsExactly('primera pregunta', 'respuesta del modelo', 'segunda pregunta')

		assertThat(messageService.countByConversation(conversationId)).isEqualTo(4L)
	}

	@Test
	void 'POST v1 chats devuelve 502 si la respuesta del modelo no trae choices'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)
		when(completionsClient.complete(any(CompletionRequest)))
				.thenReturn(new CompletionResponse(choices: []))

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'hola', '0.7', 'false')))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath('$.detail', containsString('sin choices')))

		// Sólo quedó el message del usuario: no se guardó nada del modelo
		assertThat(messageService.countByConversation(conversationId)).isEqualTo(1L)
	}

	@Test
	void 'POST v1 chats devuelve 404 si la conversacion no existe'() {
		def userId = nuevoUsuario()

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', UUID.randomUUID().toString(), userId.toString(),
						'user', 'hola', '0.7', 'false')))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath('$.detail', containsString('Conversación no encontrada')))

		verify(completionsClient, never()).complete(any(CompletionRequest))
	}

	@Test
	void 'POST v1 chats devuelve 403 si la conversacion no pertenece al usuario'() {
		def duenio = nuevoUsuario()
		// La conversación pertenece a 'duenio', pero llega otro user_id
		def conversationId = nuevaConversacion(duenio)
		def otroUsuario = nuevoUsuario()

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), otroUsuario.toString(),
						'user', 'hola', '0.7', 'false')))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath('$.detail', containsString('no pertenece al usuario')))

		verify(completionsClient, never()).complete(any(CompletionRequest))
	}

	@Test
	void 'POST v1 chats devuelve 400 si falta el modelo'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('', conversationId.toString(), userId.toString(),
						'user', 'hola', '0.7', 'false')))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath('$.errors[0].field').value('model'))

		verify(completionsClient, never()).complete(any(CompletionRequest))
	}

	@Test
	void 'POST v1 chats devuelve 400 si el rol del message no esta permitido'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'banana', 'hola', '0.7', 'false')))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath('$.detail', containsString('role')))

		verify(completionsClient, never()).complete(any(CompletionRequest))
	}
}
