package com.ia.backend.chat.service

/**
 * Herramienta invocable por el modelo dentro del flujo de /v1/chats.
 *
 * El modelo responde con "nombre-herramienta: la consulta" (por ejemplo
 * "internet-tool: recetas de comal") y el workflow busca el nombre en el
 * ToolRegistry: si existe, ejecuta la herramienta, que inyecta su resultado en
 * la conversación como messages nuevos; después se vuelve a llamar al modelo
 * con ese historial ya enriquecido.
 *
 * Para agregar una herramienta nueva basta con crear un @Service que implemente
 * esta interfaz: el ToolRegistry la registra automáticamente por su name(), sin
 * tocar WorkflowService.
 */
interface ChatTool {

	/** Nombre con el que el modelo la invoca (la parte antes de los dos puntos). */
	String name()

	/**
	 * Ejecuta la herramienta y persiste su contexto en la conversación.
	 *
	 * @param conversationId conversación del chat en curso
	 * @param consulta texto que el modelo quiere ejecutar (lo que va tras los dos puntos)
	 */
	void execute(UUID conversationId, String consulta)
}
