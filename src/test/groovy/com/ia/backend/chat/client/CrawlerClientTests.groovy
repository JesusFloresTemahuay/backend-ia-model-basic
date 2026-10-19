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
 * Prueba el cliente de crawling contra un servidor HTTP local: verifica el POST
 * con body JSON, la cabecera Authorization Bearer y el mapeo de la respuesta.
 */
class CrawlerClientTests {

	private static final String SAMPLE_JSON = '''{
	  "url": "https://masienda.com/es/blogs/learn/how-our-clay-comal-is-made",
	  "filter": "fit",
	  "query": null,
	  "cache": "0",
	  "markdown": "[ Ir al contenido ](https://masienda.com/es/blogs/learn/how-our-clay-comal-is-made#MainContent)\\n6 de noviembre de 2023\\nDo\\u00f1a Victoria, artesana del barro, utiliza un proceso centenario para elaborar los comales de barro.",
	  "success": true
	}'''

	private static final String BASE_URL_PATH = 'https://masienda.com/es/blogs/learn/how-our-clay-comal-is-made'

	private HttpServer server
	private String baseUrl
	private String lastMethod
	private String lastAuthorization
	private String lastContentType
	private String lastBody

	@BeforeEach
	void setUp() {
		server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
		server.createContext('/crawl', { HttpExchange exchange ->
			lastMethod = exchange.requestMethod
			lastAuthorization = exchange.requestHeaders.getFirst('Authorization')
			lastContentType = exchange.requestHeaders.getFirst('Content-Type')
			lastBody = exchange.requestBody.getText('UTF-8')
			responder(exchange, 200, SAMPLE_JSON)
		} as HttpHandler)
		server.createContext('/unauthorized', { HttpExchange exchange ->
			lastBody = exchange.requestBody.getText('UTF-8')
			responder(exchange, 401, '{"error":"invalid token"}')
		} as HttpHandler)
		server.start()
		baseUrl = "http://localhost:${server.address.port}/crawl" as String
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

	private static CrawlerClient cliente(String url) {
		new CrawlerClient(url, 'token-de-prueba')
	}

	@Test
	void 'envia un POST con body json y cabecera Authorization bearer'() {
		def response = cliente(baseUrl).crawl(BASE_URL_PATH)

		assertThat(lastMethod).isEqualTo('POST')
		assertThat(lastAuthorization).isEqualTo('Bearer token-de-prueba')
		assertThat(lastContentType).contains('application/json')
		assertThat(lastBody).contains('"url":"' + BASE_URL_PATH + '"')
		assertThat(response.url).isEqualTo(BASE_URL_PATH)
	}

	@Test
	void 'mapea la respuesta del crawler'() {
		def response = cliente(baseUrl).crawl(BASE_URL_PATH)

		assertThat(response.url).isEqualTo(BASE_URL_PATH)
		assertThat(response.filter).isEqualTo('fit')
		assertThat(response.query).isNull()
		assertThat(response.cache).isEqualTo('0')
		assertThat(response.success).isTrue()
		assertThat(response.markdown).contains('Doña Victoria, artesana del barro')
		assertThat(response.markdown).contains('\n6 de noviembre de 2023')
	}

	@Test
	void 'lanza CrawlerClientException cuando la API responde 401'() {
		def client = new CrawlerClient(baseUrl.replace('/crawl', '/unauthorized'), 'token-malo')

		assertThatThrownBy { client.crawl(BASE_URL_PATH) }
				.isInstanceOf(CrawlerClientException)
				.hasMessageContaining('401')
	}

	@Test
	void 'valida la configuracion del crawler'() {
		assertThatThrownBy { new CrawlerClient('', 'token').crawl(BASE_URL_PATH) }
				.isInstanceOf(CrawlerClientException)
				.hasMessageContaining('EXTERNAL_API_CRAWLER')

		assertThatThrownBy { new CrawlerClient(baseUrl, '').crawl(BASE_URL_PATH) }
				.isInstanceOf(CrawlerClientException)
				.hasMessageContaining('EXTERNAL_API_CRAWLER_AUTH')
	}

	@Test
	void 'valida la url a rastrear'() {
		assertThatThrownBy { cliente(baseUrl).crawl('   ') }
				.isInstanceOf(CrawlerClientException)
				.hasMessageContaining('no puede estar vacía')

		assertThatThrownBy { cliente(baseUrl).crawl('ftp://ejemplo.com/archivo') }
				.isInstanceOf(CrawlerClientException)
				.hasMessageContaining('http(s)')

		assertThatThrownBy { cliente(baseUrl).crawl('no es una url') }
				.isInstanceOf(CrawlerClientException)
				.hasMessageContaining('no es válida')
	}
}
