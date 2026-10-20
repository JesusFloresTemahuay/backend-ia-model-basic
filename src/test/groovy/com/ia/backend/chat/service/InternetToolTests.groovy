package com.ia.backend.chat.service

import com.ia.backend.chat.client.CrawlResponse
import com.ia.backend.chat.client.CrawlerClient
import com.ia.backend.chat.client.CrawlerClientException
import com.ia.backend.chat.client.SearchResponse
import com.ia.backend.chat.client.SearchResult
import com.ia.backend.chat.client.SearcherClient
import com.ia.backend.chat.entity.Message
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

import static org.assertj.core.api.Assertions.assertThat
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.times
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.verifyNoInteractions
import static org.mockito.Mockito.when

/**
 * Prueba InternetTool con los clientes de búsqueda y crawling mockeados:
 * percent-encode de la consulta, orden por score, saltos de urls sin markdown
 * y parada al reunir 4 resultados con markdown.
 */
class InternetToolTests {

	private static final UUID CONVERSATION = UUID.randomUUID()

	private SearcherClient searcherClient
	private CrawlerClient crawlerClient
	private MessageService messageService
	private InternetTool tool

	@BeforeEach
	void setUp() {
		searcherClient = Mockito.mock(SearcherClient)
		crawlerClient = Mockito.mock(CrawlerClient)
		messageService = Mockito.mock(MessageService)
		tool = new InternetTool(searcherClient, crawlerClient, messageService)
	}

	private static SearchResult resultado(double score, String url) {
		new SearchResult(score: score, url: url, title: 'titulo')
	}

	private static CrawlResponse crawl(String url, String markdown) {
		new CrawlResponse(url: url, success: true, markdown: markdown)
	}

	@Test
	void 'percent-encodea la consulta antes de enviarla al buscador'() {
		tool.execute(CONVERSATION, 'como hacer un comal')

		verify(searcherClient).search('como+hacer+un+comal')
	}

	@Test
	void 'percent-encodea acentos y simbolos sin doble codificar'() {
		tool.execute(CONVERSATION, 'qué es un comal & tortillas')

		verify(searcherClient).search('qu%C3%A9+es+un+comal+%26+tortillas')
	}

	@Test
	void 'sin resultados no rastrea ni guarda nada'() {
		when(searcherClient.search(anyString())).thenReturn(new SearchResponse(results: []))

		tool.execute(CONVERSATION, 'algo')

		verifyNoInteractions(crawlerClient, messageService)
	}

	@Test
	void 'rastrea las urls ordenadas de mayor a menor score y se detiene en 4 markdowns'() {
		when(searcherClient.search(anyString())).thenReturn(new SearchResponse(results: [
				resultado(0.2, 'https://a'), resultado(0.9, 'https://b'), resultado(0.5, 'https://c'),
				resultado(0.1, 'https://d'), resultado(0.8, 'https://e'), resultado(0.7, 'https://f')
		]))
		['b', 'c', 'e', 'f', 'd', 'a'].each { letra ->
			String url = 'https://' + letra
			when(crawlerClient.crawl(url)).thenReturn(crawl(url, 'markdown ' + letra))
		}

		tool.execute(CONVERSATION, 'comal')

		// Orden por score (b 0.9, e 0.8, f 0.7, c 0.5, ...): sólo se rastrean 4
		def captor = ArgumentCaptor.forClass(String)
		verify(crawlerClient, times(4)).crawl(captor.capture())
		assertThat(captor.allValues).containsExactly('https://b', 'https://e', 'https://f', 'https://c')

		// ... y se guardan exactamente 4 messages con rol tool
		ArgumentCaptor<Message> guardados = ArgumentCaptor.forClass(Message)
		verify(messageService, times(4)).create(guardados.capture())
		assertThat(guardados.allValues*.content)
				.containsExactly('markdown b', 'markdown e', 'markdown f', 'markdown c')
		assertThat(guardados.allValues*.role).containsOnly('tool')
		assertThat(guardados.allValues*.conversationId).containsOnly(CONVERSATION)
	}

	@Test
	void 'salta las urls sin markdown y sigue hasta agotar los resultados'() {
		when(searcherClient.search(anyString())).thenReturn(new SearchResponse(results: [
				resultado(0.9, 'https://falla'), resultado(0.5, 'https://vacio'), resultado(0.1, 'https://ok')
		]))
		when(crawlerClient.crawl('https://falla')).thenThrow(new CrawlerClientException('HTTP 500'))
		when(crawlerClient.crawl('https://vacio')).thenReturn(crawl('https://vacio', null))
		when(crawlerClient.crawl('https://ok')).thenReturn(crawl('https://ok', 'markdown ok'))

		tool.execute(CONVERSATION, 'comal')

		// Las tres urls se intentan (falla -> salta, sin markdown -> salta, ok -> guarda)
		verify(crawlerClient).crawl('https://falla')
		verify(crawlerClient).crawl('https://vacio')
		verify(crawlerClient).crawl('https://ok')

		ArgumentCaptor<Message> guardado = ArgumentCaptor.forClass(Message)
		verify(messageService, times(1)).create(guardado.capture())
		assertThat(guardado.value.role).isEqualTo('tool')
		assertThat(guardado.value.content).isEqualTo('markdown ok')
	}

	@Test
	void 'guarda el markdown con rol tool y la herramienta en metadata'() {
		when(searcherClient.search(anyString())).thenReturn(new SearchResponse(results: [
				resultado(0.5, 'https://ok')
		]))
		when(crawlerClient.crawl('https://ok')).thenReturn(crawl('https://ok', '# Guía del comal'))

		tool.execute(CONVERSATION, 'comal')

		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message)
		verify(messageService).create(captor.capture())

		def guardado = captor.value
		assertThat(guardado.conversationId).isEqualTo(CONVERSATION)
		assertThat(guardado.role).isEqualTo('tool')                     // rol permitido por el CHECK
		assertThat(guardado.content).isEqualTo('# Guía del comal')
		assertThat(guardado.message).isEqualTo('# Guía del comal')
		assertThat(guardado.metadata).containsEntry('tool', 'internet-tool')
	}

	@Test
	void 'el nombre con el que el modelo la invoca es internet-tool'() {
		assertThat(tool.name()).isEqualTo('internet-tool')
	}
}
