package com.ia.backend.chat.service

import com.ia.backend.chat.client.CrawlerClient
import com.ia.backend.chat.client.CrawlerClientException
import com.ia.backend.chat.client.SearcherClient
import com.ia.backend.chat.entity.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import java.nio.charset.StandardCharsets

/**
 * Herramienta "internet-tool": la que el modelo invoca respondiendo
 * "internet-tool: lo que se va a buscar en internet".
 *
 * Flujo:
 *  1. Percent-encodea la consulta para compartirla con el endpoint del buscador
 *     (?q=...): esto lo hace el SearcherClient sólo si la consulta ya viene
 *     codificada, por eso se codifica aquí (evita doble codificación).
 *  2. Llama al cliente de búsqueda y ordena los resultados por score (mayor a menor).
 *  3. Itera los resultados: usa la url de cada uno como entrada del cliente de
 *     crawling y, si devuelve markdown, lo persiste como message de la
 *     conversación con rol "tool" (rol permitido por el CHECK de la base de
 *     datos) y el nombre de la herramienta en metadata {"tool": "internet-tool"},
 *     para que entre al contexto del modelo.
 *  4. Termina al reunir RESULTADOS_CON_MARKDOWN markdowns o al agotar resultados.
 */
@Service
class InternetTool implements ChatTool {

	static final String NOMBRE = 'internet-tool'

	/** Número de resultados con markdown que se inyectan en el contexto. */
	static final int RESULTADOS_CON_MARKDOWN = 4

	private static final log = LoggerFactory.getLogger(InternetTool)

	private final SearcherClient searcherClient
	private final CrawlerClient crawlerClient
	private final MessageService messageService

	InternetTool(SearcherClient searcherClient, CrawlerClient crawlerClient, MessageService messageService) {
		this.searcherClient = searcherClient
		this.crawlerClient = crawlerClient
		this.messageService = messageService
	}

	@Override
	String name() {
		NOMBRE
	}

	@Override
	void execute(UUID conversationId, String consulta) {
		// 1. Percent-encode: la consulta viaja "compartible" con el endpoint (?q=...)
		def encoded = URLEncoder.encode(consulta.trim(), StandardCharsets.UTF_8)

		// 2. Búsqueda ordenada por score (de mayor a menor)
		def results = searcherClient.search(encoded)?.results ?: []
		def ordenados = results.findAll { it?.url }.sort { -(it.score ?: 0d) }

		// 3. Crawl iterativo hasta juntar 4 markdowns; las urls que fallan se saltan
		int guardados = 0
		for (def result : ordenados) {
			if (guardados >= RESULTADOS_CON_MARKDOWN) {
				break
			}
			try {
				def markdown = crawlerClient.crawl(result.url)?.markdown
				if (markdown?.trim()) {
					// Rol fijo 'tool' (CHECK de la base de datos); el nombre de la
					// herramienta queda identificable en metadata
					messageService.create(new Message(
							conversationId: conversationId,
							role: 'tool',
							content: markdown,
							message: markdown,
							metadata: ['tool': NOMBRE]))
					guardados++
				} else {
					log.debug('internet-tool: [{}] no devolvió markdown, se salta', result.url)
				}
			} catch (CrawlerClientException e) {
				log.warn('internet-tool: no se pudo rastrear [{}]: {}', result.url, e.message)
			}
		}

		log.info('internet-tool: consulta [{}] -> {} results, {} markdowns inyectados',
				consulta, ordenados.size(), guardados)

		if (guardados == 0) {
			log.warn('internet-tool: ningún resultado de [{}] devolvió markdown', consulta)
		}
	}
}
