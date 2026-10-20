package com.ia.backend.chat.controller

import com.ia.backend.chat.client.CompletionChoice
import com.ia.backend.chat.client.CompletionMessage
import com.ia.backend.chat.client.CompletionRequest
import com.ia.backend.chat.client.CompletionResponse
import com.ia.backend.chat.client.CompletionUsage
import com.ia.backend.chat.client.CompletionsClient
import com.ia.backend.chat.client.CrawlResponse
import com.ia.backend.chat.client.CrawlerClient
import com.ia.backend.chat.client.CrawlerClientException
import com.ia.backend.chat.client.SearchResponse
import com.ia.backend.chat.client.SearchResult
import com.ia.backend.chat.client.SearcherClient
import com.ia.backend.chat.entity.Conversation
import com.ia.backend.chat.entity.User
import com.ia.backend.chat.repository.ConversationRepository
import com.ia.backend.chat.repository.UserRepository
import com.ia.backend.chat.service.MessageService
import com.ia.backend.chat.service.WorkflowService
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
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.never
import static org.mockito.Mockito.times
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Flujo completo de POST /v1/chats con los clientes externos (completions,
 * búsqueda y crawling) mockeados.
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

	@MockitoBean
	SearcherClient searcherClient

	@MockitoBean
	CrawlerClient crawlerClient

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

	/** Respuesta simulada del modelo con el content indicado. */
	private static CompletionResponse respuesta(String id, String content) {
		new CompletionResponse(
				created: 1792403447L,
				object: 'chat.completion',
				id: id,
				model: 'un_modelo',
				choices: [new CompletionChoice(index: 0, finishReason: 'stop',
						message: new CompletionMessage(role: 'assistant', content: content,
								reasoning: 'un razonamiento'))],
				usage: new CompletionUsage(promptTokens: 10, completionTokens: 5, totalTokens: 15))
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
				.containsExactly('system', 'user', 'assistant', 'user')
		assertThat(segundaLlamada.messages).extracting('content')
				.containsExactly(WorkflowService.INSTRUCCION_INICIAL, 'primera pregunta',
						'respuesta del modelo', 'segunda pregunta')

		assertThat(messageService.countByConversation(conversationId)).isEqualTo(5L)
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

		// Quedaron la instrucción de sistema y el message del usuario:
		// no se guardó nada del modelo
		assertThat(messageService.countByConversation(conversationId)).isEqualTo(2L)
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

	@Test
	void 'un chat nuevo carga la instruccion de sistema y no la duplica'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'hola', '0.7', 'false')))
				.andExpect(status().isOk())

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'sigue', '0.7', 'false')))
				.andExpect(status().isOk())

		def captor = ArgumentCaptor.forClass(CompletionRequest)
		verify(completionsClient, times(2)).complete(captor.capture())

		// Ambas llamadas arrancan con la instrucción de sistema...
		assertThat(captor.allValues[0].messages[0].role).isEqualTo('system')
		assertThat(captor.allValues[0].messages[0].content).isEqualTo(WorkflowService.INSTRUCCION_INICIAL)
		assertThat(captor.allValues[1].messages[0].role).isEqualTo('system')

		// ... y sólo se insertó una vez, en position 0
		def guardados = messageService.findByConversation(conversationId)
		assertThat(guardados[0].role).isEqualTo('system')
		assertThat(guardados[0].content).isEqualTo(WorkflowService.INSTRUCCION_INICIAL)
		assertThat(guardados.count { it.role == 'system' }).isEqualTo(1)
	}

	@Test
	void 'ejecuta internet-tool inyecta los markdowns y vuelve a preguntar al modelo'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		// Primera respuesta: pide la herramienta; segunda: respuesta final
		when(completionsClient.complete(any(CompletionRequest))).thenReturn(
				respuesta('primera', 'internet-tool: como hacer un comal'),
				respuesta('segunda', 'respuesta final'))

		// Búsqueda con scores desordenados (b 0.9, c 0.5, a 0.2)
		when(searcherClient.search('como+hacer+un+comal')).thenReturn(new SearchResponse(results: [
				new SearchResult(score: 0.2, url: 'https://a'),
				new SearchResult(score: 0.9, url: 'https://b'),
				new SearchResult(score: 0.5, url: 'https://c')
		]))
		// Crawler: b y c devuelven markdown, a falla y se salta
		when(crawlerClient.crawl('https://b'))
				.thenReturn(new CrawlResponse(url: 'https://b', markdown: 'markdown de b'))
		when(crawlerClient.crawl('https://c'))
				.thenReturn(new CrawlResponse(url: 'https://c', markdown: 'markdown de c'))
		when(crawlerClient.crawl('https://a')).thenThrow(new CrawlerClientException('sin markdown'))

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'busca como hacer un comal', '0.7', 'false')))
				.andExpect(status().isOk())
				.andExpect(jsonPath('$.id').value('segunda'))
				.andExpect(jsonPath('$.choices[0].message.content').value('respuesta final'))

		// Llamó al buscador con la consulta percent-encoded y rastreó por score
		verify(searcherClient).search('como+hacer+un+comal')
		def crawls = ArgumentCaptor.forClass(String)
		verify(crawlerClient, times(3)).crawl(crawls.capture())
		assertThat(crawls.allValues).containsExactly('https://b', 'https://c', 'https://a')

		// La segunda llamada al modelo llevó el contexto inyectado (rol tool)
		def captor = ArgumentCaptor.forClass(CompletionRequest)
		verify(completionsClient, times(2)).complete(captor.capture())
		def segunda = captor.allValues[1]
		assertThat(segunda.messages).extracting('role')
				.containsExactly('system', 'user', 'assistant', 'tool', 'tool')
		assertThat(segunda.messages).extracting('content')
				.containsExactly(WorkflowService.INSTRUCCION_INICIAL, 'busca como hacer un comal',
						'internet-tool: como hacer un comal', 'markdown de b', 'markdown de c')

		// Historial final: system, user, assistant(petición), 2 tool, assistant(final)
		def guardados = messageService.findByConversation(conversationId)
		assertThat(guardados*.role).containsExactly('system', 'user', 'assistant',
				'tool', 'tool', 'assistant')
		assertThat(guardados*.content).contains('markdown de b', 'markdown de c')

		// Los messages de la herramienta identifican de qué herramienta vienen
		guardados.findAll { it.role == 'tool' }.each { m ->
			assertThat(m.metadata).containsEntry('tool', 'internet-tool')
		}
		assertThat(messageService.countByConversation(conversationId)).isEqualTo(6L)
	}

	@Test
	void 'ignora una respuesta que nombra una herramienta no registrada'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)
		when(completionsClient.complete(any(CompletionRequest)))
				.thenReturn(respuesta('unica', 'otra-herramienta: busca algo'))

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'hola', '0.7', 'false')))
				.andExpect(status().isOk())
				.andExpect(jsonPath('$.choices[0].message.content').value('otra-herramienta: busca algo'))

		// No se lanzó ninguna herramienta: una sola llamada al modelo y clientes sin usar
		verify(completionsClient, times(1)).complete(any(CompletionRequest))
		verify(searcherClient, never()).search(anyString())
		verify(crawlerClient, never()).crawl(anyString())
		assertThat(messageService.countByConversation(conversationId)).isEqualTo(3L)
	}

	@Test
	void 'no lanza la herramienta si el modelo devuelve el marcador sin sustituir'() {
		def userId = nuevoUsuario()
		def conversationId = nuevaConversacion(userId)

		// 1.ª: la plantilla legada literal; 2.ª: el marcador nuevo <texto a buscar>
		when(completionsClient.complete(any(CompletionRequest))).thenReturn(
				respuesta('marcador-1', 'internet-tool: la busqueda'),
				respuesta('marcador-2', 'internet-tool: <texto a buscar>'))

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'busca el precio del comal', '0.7', 'false')))
				.andExpect(status().isOk())
				.andExpect(jsonPath('$.choices[0].message.content').value('internet-tool: la busqueda'))

		mockMvc.perform(post('/v1/chats')
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload('un_modelo', conversationId.toString(), userId.toString(),
						'user', 'sigue', '0.7', 'false')))
				.andExpect(status().isOk())
				.andExpect(jsonPath('$.choices[0].message.content').value('internet-tool: <texto a buscar>'))

		// Nunca se buscó con el marcador: sólo system, users y assistants en el historial
		verify(searcherClient, never()).search(anyString())
		verify(crawlerClient, never()).crawl(anyString())
		verify(completionsClient, times(2)).complete(any(CompletionRequest))
		assertThat(messageService.findByConversation(conversationId)*.role)
				.containsExactly('system', 'user', 'assistant', 'user', 'assistant')
	}
}
