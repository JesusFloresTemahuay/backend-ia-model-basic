package com.ia.backend.chat.service

import org.junit.jupiter.api.Test
import org.mockito.Mockito

import static org.assertj.core.api.Assertions.assertThat

/**
 * El registro indexa las herramientas por el nombre con el que el modelo las
 * invoca: así el criterio de nombre es escalable (una herramienta nueva se
 * registra sola al crear su @Service, sin tocar el workflow).
 */
class ToolRegistryTests {

	@Test
	void 'encuentra la herramienta por su nombre'() {
		def internet = Mockito.mock(ChatTool)
		Mockito.when(internet.name()).thenReturn('internet-tool')
		def registry = new ToolRegistry([internet])

		assertThat(registry.find('internet-tool')).contains(internet)
	}

	@Test
	void 'devuelve vacio para nombres no registrados'() {
		def registry = new ToolRegistry([])

		assertThat(registry.find('otra-herramienta')).isEmpty()
		assertThat(registry.find(null)).isEmpty()
		assertThat(registry.find('')).isEmpty()
	}

	@Test
	void 'registra varias herramientas con el mismo criterio'() {
		def internet = Mockito.mock(ChatTool)
		Mockito.when(internet.name()).thenReturn('internet-tool')
		def futura = Mockito.mock(ChatTool)
		Mockito.when(futura.name()).thenReturn('otra-tool')
		def registry = new ToolRegistry([internet, futura])

		assertThat(registry.nombres()).containsExactlyInAnyOrder('internet-tool', 'otra-tool')
		assertThat(registry.find('otra-tool')).contains(futura)
	}
}
