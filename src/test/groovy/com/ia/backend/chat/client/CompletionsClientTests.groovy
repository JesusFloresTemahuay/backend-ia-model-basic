package com.ia.backend.chat.client

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * Prueba el cliente de completions contra un servidor HTTP local: verifica el POST
 * con el body parametrizado (model, messages, temperature, stream) y el mapeo de
 * la respuesta chat.completion.
 */
class CompletionsClientTests {

	private static final String SAMPLE_JSON = '''{
	  "created": 1792403447,
	  "object": "chat.completion",
	  "id": "a26c09ba-0b72-4ac4-a9f6-765f332b67fb",
	  "model": "qwen3.8-4b-q4",
	  "choices": [
	    {
	      "index": 0,
	      "finish_reason": "stop",
	      "message": {
	        "role": "assistant",
	        "content": "respuesta al usuario",
	        "reasoning": "un razonamiento"
	      }
	    }
	  ],
	  "usage": {
	    "prompt_tokens": 32,
	    "completion_tokens": 714,
	    "total_tokens": 746
	  }
	}'''

	private HttpServer server
	private String baseUrl
	private String lastMethod
	private String lastContentType
	private String lastBody

	@BeforeEach
	void setUp() {
		server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
		server.createContext('/completions', { HttpExchange exchange ->
			lastMethod = exchange.requestMethod
			lastContentType = exchange.requestHeaders.getFirst('Content-Type')
			lastBody = exchange.requestBody.getText('UTF-8')
			responder(exchange, 200, SAMPLE_JSON)
		} as HttpHandler)
		server.createContext('/error', { HttpExchange exchange ->
			exchange.requestBody.getText('UTF-8')
			responder(exchange, 500, '{"error":"internal"}')
		} as HttpHandler)
		server.start()
		baseUrl = "http://localhost:${server.address.port}/completions" as String
	}

	@AfterEach
	void tearDown() {
		server?.stop(0)
	}

	private static void responder(HttpExchange exchange, int status, String body) {
		def bytes = body.getBytes(StandardCharsets.UTF_8)
		exchange.responseHeaders.add('Content-Type', 'application/json; charset=utf-8')
		exchange.sendResponseHeaders(status, bytes.length)
		exchange.responseBody.withCloseable { it.write(bytes) }
	}

	private static CompletionsClient cliente(String url) {
		new CompletionsClient(url)
	}

	@Test
	void 'envia un POST con el body parametrizado'() {
		def request = new CompletionRequest(
				model: 'otro-modelo-7b',
				temperature: 0.2d,
				stream: true,
				messages: [
						new ChatMessage(role: 'system', content: 'Eres un asistente'),
						new ChatMessage(role: 'user', content: 'Hola')
				])

		cliente(baseUrl).complete(request)

		assertThat(lastMethod).isEqualTo('POST')
		assertThat(lastContentType).contains('application/json')
		assertThat(lastBody).contains('"model":"otro-modelo-7b"')
		assertThat(lastBody).contains('"temperature":0.2')
		assertThat(lastBody).contains('"stream":true')
		assertThat(lastBody).contains('"role":"system"')
		assertThat(lastBody).contains('"content":"Eres un asistente"')
		assertThat(lastBody).contains('"role":"user"')
		assertThat(lastBody).contains('"content":"Hola"')
		assertThat(lastBody.count('"role"')).isEqualTo(2) // más de un message
	}

	@Test
	void 'aplica los valores por defecto de temperature y stream'() {
		cliente(baseUrl).complete(new CompletionRequest(
				model: 'qwen3.8-4b-q4',
				messages: [new ChatMessage(role: 'user', content: 'Hola')]))

		assertThat(lastBody).contains('"temperature":0.7')
		assertThat(lastBody).contains('"stream":false')
	}

	@Test
	void 'mapea la respuesta chat completion'() {
		def response = cliente(baseUrl).complete('qwen3.8-4b-q4', 'Hola')

		assertThat(response.created).isEqualTo(1792403447L)
		assertThat(response.object).isEqualTo('chat.completion')
		assertThat(response.id).isEqualTo('a26c09ba-0b72-4ac4-a9f6-765f332b67fb')
		assertThat(response.model).isEqualTo('qwen3.8-4b-q4')

		assertThat(response.choices).hasSize(1)
		def choice = response.choices[0]
		assertThat(choice.index).isEqualTo(0)
		assertThat(choice.finishReason).isEqualTo('stop')      // finish_reason
		assertThat(choice.message.role).isEqualTo('assistant')
		assertThat(choice.message.content).isEqualTo('respuesta al usuario')
		assertThat(choice.message.reasoning).isEqualTo('un razonamiento')

		assertThat(response.usage.promptTokens).isEqualTo(32)          // prompt_tokens
		assertThat(response.usage.completionTokens).isEqualTo(714)     // completion_tokens
		assertThat(response.usage.totalTokens).isEqualTo(746)          // total_tokens
	}

	@Test
	void 'lanza CompletionsClientException cuando la API responde un error HTTP'() {
		def client = new CompletionsClient(baseUrl.replace('/completions', '/error'))

		assertThatThrownBy { client.complete('qwen3.8-4b-q4', 'Hola') }
				.isInstanceOf(CompletionsClientException)
				.hasMessageContaining('500')
	}

	@Test
	void 'valida la configuracion y la peticion'() {
		assertThatThrownBy { new CompletionsClient('').complete('modelo', 'Hola') }
				.isInstanceOf(CompletionsClientException)
				.hasMessageContaining('EXTERNAL_API_COMPLETIONS')

		assertThatThrownBy { cliente(baseUrl).complete((CompletionRequest) null) }
				.isInstanceOf(CompletionsClientException)
				.hasMessageContaining('no puede ser nula')

		assertThatThrownBy { cliente(baseUrl).complete(new CompletionRequest()) }
				.isInstanceOf(CompletionsClientException)
				.hasMessageContaining('model')

		assertThatThrownBy { cliente(baseUrl).complete(new CompletionRequest(
				model: 'm', messages: [new ChatMessage(role: 'user', content: '   ')])) }
				.isInstanceOf(CompletionsClientException)
				.hasMessageContaining('role y content')
	}
}
