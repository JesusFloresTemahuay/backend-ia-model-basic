package com.ia.backend.chat.service

import org.springframework.stereotype.Service

/**
 * Registro de las herramientas disponibles para el flujo de chat, indexadas por
 * el nombre con el que el modelo las invoca (la parte antes de los dos puntos).
 *
 * Cualquier @Service que implemente ChatTool queda registrado automáticamente
 * al arrancar la aplicación: así el criterio de nombre es escalable y las
 * herramientas futuras se llaman exactamente de la misma forma.
 */
@Service
class ToolRegistry {

	private final Map<String, ChatTool> porNombre = [:]

	ToolRegistry(List<ChatTool> herramientas) {
		herramientas.each { porNombre[it.name()] = it }
	}

	/**
	 * @return la herramienta registrada con ese nombre, o vacío si no existe
	 */
	Optional<ChatTool> find(String nombre) {
		Optional.ofNullable(nombre ? porNombre[nombre] : null)
	}

	/** Nombres registrados (para diagnósticos/tests). */
	Set<String> nombres() {
		porNombre.keySet()
	}
}
