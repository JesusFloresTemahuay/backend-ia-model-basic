package com.ia.backend.chat.service

import com.ia.backend.chat.client.ChatMessage
import com.ia.backend.chat.client.CompletionRequest
import com.ia.backend.chat.client.CompletionResponse
import com.ia.backend.chat.client.CompletionsClient
import com.ia.backend.chat.client.CompletionsClientException
import com.ia.backend.chat.dto.ChatRequest
import com.ia.backend.chat.entity.Message
import com.ia.backend.chat.repository.ConversationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import java.util.regex.Pattern

/**
 * Flujo de chat de /v1/chats:
 *
 *  1. Valida que la conversación exista y pertenezca al user_id recibido.
 *  2. Si es un chat nuevo (la conversación no tiene messages), carga por defecto
 *     la instrucción de sistema que enseña al modelo a invocar la herramienta de
 *     búsqueda; queda guardada en el historial para las siguientes llamadas.
 *  3. Persiste el message entrante (sólo role y content del payload; position
 *     automática y model = model del payload).
 *  4. Consulta el historial completo ordenado por position ascendente, lo
 *     remapea a sólo role y content y llama al cliente de completions con el
 *     model, temperature y stream del payload.
 *  5. Persiste la respuesta del modelo como un message más de la conversación:
 *     role y content del choice, y reasoning en la columna message.
 *  6. Si la respuesta del modelo es una llamada a herramienta
 *     ("internet-tool: la búsqueda"), la ejecuta mediante el ToolRegistry: los
 *     resultados se inyectan en la conversación como messages nuevos y se vuelve
 *     a llamar al modelo con ese historial enriquecido, hasta un máximo de
 *     MAX_RONDAS_HERRAMIENTAS rondas por llamada al endpoint.
 *
 * Sin @Transactional global a propósito: la llamada al modelo puede tardar hasta
 * 5 minutos y no debe mantener ocupada una conexión de la base de datos. Cada
 * acceso a datos ocurre en la transacción corta de su propio servicio.
 */
@Service
class WorkflowService {

	/**
	 * Instrucción que se carga por defecto al iniciar un chat nuevo y que se
	 * guarda en el historial: le dice al modelo cuándo y cómo invocar la
	 * herramienta de búsqueda.
	 *
	 * Diseño anti-copia-literal: el formato va con un marcador <texto a buscar>
	 * que se declara como sustituible, más un ejemplo ya relleno (los modelos
	 * imitan el ejemplo, no la plantilla). Antes la instrucción citaba la frase
	 * completa entre comillas y el modelo respondía "internet-tool: la busqueda"
	 * sin sustituir nada.
	 */
	static final String INSTRUCCION_INICIAL =
			'Herramienta de búsqueda en internet (internet-tool). Úsala SOLO cuando el usuario ' +
			'pida explícitamente buscar en internet; en cualquier otro caso responde con normalidad ' +
			'y sin usarla.\n' +
			'\n' +
			'Cuando corresponda usarla, responde exactamente una línea y nada más:\n' +
			'internet-tool: <texto a buscar>\n' +
			'<texto a buscar> es un marcador: reemplázalo SIEMPRE por la consulta concreta que ' +
			'deduces de lo que pide el usuario, escrita con sus palabras. Nunca dejes el marcador ' +
			'<> sin sustituir ni respondas con el formato vacío.\n' +
			'\n' +
			'Ejemplo:\n' +
			'Usuario: ¿Cuánto cuesta un comal de barro?\n' +
			'Asistente: internet-tool: precio de un comal de barro\n' +
			'\n' +
			'Después de la búsqueda el backend inyectará los resultados en la conversación y ' +
			'entonces responderás a la pregunta del usuario con normalidad.'

	/** Rondas de herramientas máximas por llamada (cada ronda llama otra vez al modelo). */
	private static final int MAX_RONDAS_HERRAMIENTAS = 3

	/** "internet-tool: la busqueda" -> nombre de la herramienta y su consulta. */
	private static final Pattern LLAMADA_A_HERRAMIENTA =
			Pattern.compile('^([a-z][a-z0-9-]*):\\s*(.+)$', Pattern.DOTALL)

	private static final log = LoggerFactory.getLogger(WorkflowService)

	private final MessageService messageService
	private final ConversationRepository conversationRepository
	private final CompletionsClient completionsClient
	private final ToolRegistry toolRegistry

	WorkflowService(MessageService messageService,
					ConversationRepository conversationRepository,
					CompletionsClient completionsClient,
					ToolRegistry toolRegistry) {
		this.messageService = messageService
		this.conversationRepository = conversationRepository
		this.completionsClient = completionsClient
		this.toolRegistry = toolRegistry
	}

	CompletionResponse chat(ChatRequest request) {
		validarConversacion(request)

		// Chat nuevo: el historial arranca con la instrucción de uso de herramientas
		if (messageService.countByConversation(request.conversationId) == 0L) {
			messageService.create(request.conversationId, 'system', INSTRUCCION_INICIAL)
		}

		// 1. Persiste el message entrante (position automática, model = payload)
		messageService.create(
				request.conversationId,
				request.message.role,
				request.message.content,
				request.model)

		// 2. Primera llamada al modelo con el historial completo
		def respuesta = llamarAlModelo(request)

		// 3. Rondas de herramientas: si el modelo responde "<herramienta>: <consulta>"
		//    se ejecuta (inyectando contexto en la conversación) y se vuelve a
		//    preguntar con el historial ya enriquecido.
		int rondas = 0
		while (rondas < MAX_RONDAS_HERRAMIENTAS) {
			def llamada = detectarLlamada(respuesta)
			if (llamada == null) {
				break
			}
			llamada.herramienta.execute(request.conversationId, llamada.consulta)
			respuesta = llamarAlModelo(request)
			rondas++
		}

		respuesta
	}

	/**
	 * Historial completo de la conversación (sólo role y content) -> completions
	 * -> persiste la respuesta del modelo. Hasta 5 minutos de espera.
	 */
	private CompletionResponse llamarAlModelo(ChatRequest request) {
		def historial = messageService.findByConversation(request.conversationId).collect { m ->
			new ChatMessage(role: m.role, content: m.content)
		}

		def respuesta = completionsClient.complete(new CompletionRequest(
				model: request.model,
				messages: historial,
				temperature: request.temperature,
				stream: request.stream))

		guardarRespuestaDelAsistente(request, respuesta)

		respuesta
	}

	/**
	 * Detecta si la respuesta del modelo es una llamada a herramienta
	 * ("internet-tool: la busqueda"): divide el content en nombre de
	 * herramienta y consulta (separados por ":"), y sólo la considera llamada si
	 * ese nombre está registrado en el ToolRegistry, de modo que una respuesta
	 * normal que contenga dos puntos nunca dispara la herramienta.
	 *
	 * Además descarta las consultas que son sólo el marcador sin sustituir
	 * (p. ej. "internet-tool: <texto a buscar>" o la antigua plantilla literal
	 * "la busqueda" que aún puede aparecer en conversaciones iniciadas antes de
	 * este cambio): no se lanza la herramienta para no buscar basura.
	 *
	 * @return la herramienta con su consulta, o null si no es una llamada
	 */
	private LlamadaHerramienta detectarLlamada(CompletionResponse respuesta) {
		def content = respuesta?.choices ? respuesta.choices[0]?.message?.content : null
		if (!content?.trim()) {
			return null
		}

		def matcher = LLAMADA_A_HERRAMIENTA.matcher(content.trim())
		if (!matcher.matches()) {
			return null
		}

		def herramienta = toolRegistry.find(matcher.group(1))
		def consulta = matcher.group(2).trim()
		if (!consulta) {
			return null
		}

		if (esMarcadorSinSustituir(consulta)) {
			log.warn('La herramienta [{}] no se lanza: el modelo devolvió el marcador sin ' +
					'sustituir -> [{}]', matcher.group(1), content)
			return null
		}

		if (!herramienta.present) {
			return null
		}

		new LlamadaHerramienta(herramienta: herramienta.get(), consulta: consulta)
	}

	/** Marcador "<...>" completo o la plantilla legada "la busqueda". */
	private static boolean esMarcadorSinSustituir(String consulta) {
		def q = consulta.trim()
		(q.startsWith('<') && q.endsWith('>')) || q.toLowerCase() in ['la busqueda', 'texto a buscar']
	}

	/**
	 * Guarda la respuesta del modelo como un message de la conversación:
	 * role y content del primer choice, y reasoning en la columna message
	 * (si no trae reasoning se guarda content para no violar el NOT NULL).
	 */
	private void guardarRespuestaDelAsistente(ChatRequest request, CompletionResponse respuesta) {
		def choice = respuesta?.choices ? respuesta.choices[0] : null
		if (choice?.message == null) {
			throw new CompletionsClientException('La API externa de completions devolvió una respuesta sin choices')
		}

		def delModelo = choice.message
		def content = delModelo.content
		def reasoning = delModelo.reasoning

		if (!content?.trim() && !reasoning?.trim()) {
			throw new CompletionsClientException(
					'La API externa de completions devolvió un choice sin content ni reasoning')
		}

		messageService.create(new Message(
				conversationId: request.conversationId,
				role: delModelo.role?.trim() ?: 'assistant',
				content: content?.trim() ?: reasoning,
				message: reasoning ?: content,
				model: request.model))
	}

	private void validarConversacion(ChatRequest request) {
		def conversation = conversationRepository.findById(request.conversationId)
				.orElseThrow { new ConversationNotFoundException(request.conversationId) }

		if (conversation.userId != request.userId) {
			throw new ConversationAccessDeniedException(request.conversationId, request.userId)
		}
	}

	/** Nombre de herramienta + consulta de una llamada del modelo. */
	private static class LlamadaHerramienta {
		ChatTool herramienta
		String consulta
	}
}
