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
 * Prueba el cliente contra un servidor HTTP local: verifica que usa POST con
 * q y format en la query string y que la respuesta se mapea campo a campo.
 */
class SearcherClientTests {

	private static final String SAMPLE_JSON = '''{
	  "query": "como+hacer+un+comal",
	  "results": [
	    {
	      "template": "default.html",
	      "title": "C\u00f3mo hacer un comal de barro paso a paso",
	      "content": "Esta etapa ayuda a darle la forma final...",
	      "img_src": "",
	      "iframe_src": "",
	      "audio_src": "",
	      "thumbnail": "https://example.com/thumbnail.jpg",
	      "publishedDate": null,
	      "pubdate": "",
	      "length": null,
	      "views": "",
	      "author": "",
	      "metadata": "",
	      "priority": "",
	      "engines": ["google cse"],
	      "open_group": false,
	      "close_group": false,
	      "positions": [1],
	      "score": 1.0,
	      "category": "general",
	      "url": "https://www.tiktok.com/@user/video/123",
	      "engine": "google cse",
	      "parsed_url": ["https", "www.tiktok.com", "/@user/video/123", "", "", ""]
	    },
	    {
	      "template": "default.html",
	      "title": "Comal De Barro - YouTube",
	      "content": "Do\u00f1a Victoria, the resident clay artisan...",
	      "img_src": "img.png",
	      "thumbnail": "",
	      "engines": ["google cse"],
	      "open_group": false,
	      "close_group": false,
	      "positions": [2, 3],
	      "score": 0.3333333333333333,
	      "category": "general",
	      "url": "https://www.youtube.com/watch?v=NMMgvIyz8Io",
	      "engine": "google cse",
	      "parsed_url": ["https", "www.youtube.com", "/watch", "", "v=NMMgvIyz8Io", ""]
	    }
	  ],
	  "answers": [],
	  "corrections": [],
	  "infoboxes": [],
	  "suggestions": [],
	  "unresponsive_engines": [
	    ["brave", "Suspended: too many requests"],
	    ["duckduckgo", "CAPTCHA"]
	  ]
	}'''

	private HttpServer server
	private String baseUrl
	private String lastMethod
	private String lastRawQuery

	@BeforeEach
	void setUp() {
		server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
		server.createContext('/search', { HttpExchange exchange ->
			lastMethod = exchange.requestMethod
			// rawQuery: conserva el percent-encoding tal cual llega por la red
			lastRawQuery = exchange.requestURI.rawQuery
			responder(exchange, 200, SAMPLE_JSON)
		} as HttpHandler)
		server.createContext('/error', { HttpExchange exchange ->
			responder(exchange, 502, '{"error":"bad gateway"}')
		} as HttpHandler)
		server.start()
		baseUrl = "http://localhost:${server.address.port}/search" as String
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

	@Test
	void 'envia un POST con q percent-encoded y format en la query string'() {
		def response = new SearcherClient(baseUrl).search(URLEncoder.encode('como hacer un comal', StandardCharsets.UTF_8))

		assertThat(lastMethod).isEqualTo('POST')
		assertThat(lastRawQuery).isEqualTo('q=como+hacer+un+comal&format=json')
		assertThat(response.query).isEqualTo('como+hacer+un+comal')
	}

	@Test
	void 'no doble-codifica los acentos de la consulta'() {
		new SearcherClient(baseUrl).search(URLEncoder.encode('qué es el comal', StandardCharsets.UTF_8))

		assertThat(lastRawQuery).isEqualTo('q=qu%C3%A9+es+el+comal&format=json')
	}

	@Test
	void 'lanza SearcherClientException si la consulta no viene percent-encoded'() {
		assertThatThrownBy { new SearcherClient(baseUrl).search('como hacer un comal') }
				.isInstanceOf(SearcherClientException)
				.hasMessageContaining('percent-encoded')
	}

	@Test
	void 'mapea la respuesta incluidos los campos snake_case'() {
		def response = new SearcherClient(baseUrl).search('comal')

		assertThat(response.results).hasSize(2)

		def first = response.results[0]
		assertThat(first.title).isEqualTo('Cómo hacer un comal de barro paso a paso')
		assertThat(first.imgSrc).isEmpty()                 // img_src
		assertThat(first.iframeSrc).isEmpty()              // iframe_src
		assertThat(first.audioSrc).isEmpty()               // audio_src
		assertThat(first.openGroup).isFalse()              // open_group
		assertThat(first.closeGroup).isFalse()             // close_group
		assertThat(first.positions).containsExactly(1)
		assertThat(first.score).isEqualTo(1.0d)
		assertThat(first.engines).containsExactly('google cse')
		assertThat(first.url).isEqualTo('https://www.tiktok.com/@user/video/123')
		assertThat(first.parsedUrl).containsExactly('https', 'www.tiktok.com', '/@user/video/123', '', '', '')
		assertThat(first.publishedDate).isNull()          // llega como publishedDate (camelCase)

		def second = response.results[1]
		assertThat(second.imgSrc).isEqualTo('img.png')
		assertThat(second.positions).containsExactly(2, 3)
		assertThat(second.score).isEqualTo(0.3333333333333333d)

		assertThat(response.answers).isEmpty()
		assertThat(response.unresponsiveEngines).hasSize(2)
		assertThat(response.unresponsiveEngines[0]).containsExactly('brave', 'Suspended: too many requests')
		assertThat(response.unresponsiveEngines[1]).containsExactly('duckduckgo', 'CAPTCHA')
	}

	@Test
	void 'lanza SearcherClientException cuando la API responde un error HTTP'() {
		def client = new SearcherClient(baseUrl.replace('/search', '/error'))

		assertThatThrownBy { client.search('comal') }
				.isInstanceOf(SearcherClientException)
				.hasMessageContaining('502')
	}

	@Test
	void 'lanza SearcherClientException cuando no hay conexion'() {
		def client = new SearcherClient('http://localhost:1/search')

		assertThatThrownBy { client.search('comal') }
				.isInstanceOf(SearcherClientException)
				.hasMessageContaining('No se pudo contactar')
	}

	@Test
	void 'lanza SearcherClientException con consulta vacia o sin configuracion'() {
		assertThatThrownBy { new SearcherClient(baseUrl).search('   ') }
				.isInstanceOf(SearcherClientException)
				.hasMessageContaining('no puede estar vacía')

		assertThatThrownBy { new SearcherClient('  ').search('comal') }
				.isInstanceOf(SearcherClientException)
				.hasMessageContaining('EXTERNAL_API_SEARCHER')
	}
}
