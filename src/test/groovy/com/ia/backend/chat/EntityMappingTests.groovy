package com.ia.backend.chat

import com.ia.backend.chat.entity.Conversation
import com.ia.backend.chat.entity.Message
import com.ia.backend.chat.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

import java.nio.ByteBuffer

import static org.assertj.core.api.Assertions.assertThat

/**
 * Valida en tiempo de ejecución que el mapeo JPA de User, Conversation y Message
 * coincide con el esquema real: nombres de columna en snake_case, tipos UUID,
 * el JSON de 'metadata' y el ciclo completo de persistencia y lectura.
 */
@SpringBootTest
@Transactional
class EntityMappingTests {

	@Autowired
	EntityManager em

	@Test
	void 'el metamodelo expone los atributos y tipos esperados'() {
		def user = em.metamodel.entity(User)
		def conversation = em.metamodel.entity(Conversation)
		def message = em.metamodel.entity(Message)

		assertThat(user.idType.javaType).isEqualTo(UUID)
		assertThat(conversation.idType.javaType).isEqualTo(UUID)
		assertThat(message.idType.javaType).isEqualTo(UUID)

		def atributos = { managed -> managed.attributes.collect { it.name } }

		assertThat(atributos(user)).containsExactlyInAnyOrder('id', 'email', 'displayName', 'passwordHash', 'createdAt')
		assertThat(atributos(conversation)).containsExactlyInAnyOrder('id', 'userId', 'title', 'createdAt', 'updatedAt')
		assertThat(atributos(message)).containsExactlyInAnyOrder('id', 'conversationId', 'position', 'role',
				'content', 'message', 'model', 'status', 'inputTokens', 'outputTokens', 'createdAt', 'metadata')

		assertThat(conversation.getAttribute('userId').javaType).isEqualTo(UUID)
		assertThat(message.getAttribute('conversationId').javaType).isEqualTo(UUID)
		assertThat(message.getAttribute('metadata').javaType).isEqualTo(Map)
	}

	@Test
	void 'persiste y recupera respetando el esquema de la base de datos'() {
		def user = new User(email: 'ana@example.com', displayName: 'Ana', passwordHash: 'sha256$abc')
		em.persist(user)

		def conversation = new Conversation(userId: user.id, title: 'Hola')
		em.persist(conversation)

		def message = new Message(conversationId: conversation.id, position: 0L, role: 'user',
				content: 'Hola', message: 'Hola', model: 'gpt-5', inputTokens: 3, outputTokens: 1,
				metadata: [source: 'test', temperature: 0.2])
		em.persist(message)
		em.flush()

		// En modo PostgreSQL, H2 devuelve los UUID como byte[16]
		def aUuid = { v ->
			if (v instanceof byte[]) {
				def bb = ByteBuffer.wrap(v)
				return new UUID(bb.getLong(), bb.getLong())
			}
			v
		}

		// Las columnas físicas existen con los nombres snake_case del esquema
		def conversationRow = (Object[]) em.createNativeQuery(
				'select id, user_id, title, created_at, updated_at from conversations where id = :id')
				.setParameter('id', conversation.id)
				.singleResult
		assertThat(aUuid(conversationRow[0])).isEqualTo(conversation.id)
		assertThat(aUuid(conversationRow[1])).isEqualTo(user.id)

		def userRow = (Object[]) em.createNativeQuery(
				'select email, display_name, password_hash, created_at from users where id = :id')
				.setParameter('id', user.id)
				.singleResult
		assertThat(userRow[0]).isEqualTo('ana@example.com')
		assertThat(userRow[1]).isEqualTo('Ana')
		assertThat(userRow[2]).isEqualTo('sha256$abc')

		def messageRow = (Object[]) em.createNativeQuery(
				'select conversation_id, "position", role, content, message, model, status, input_tokens, output_tokens, metadata ' +
						'from messages where id = :id')
				.setParameter('id', message.id)
				.singleResult
		assertThat(aUuid(messageRow[0])).isEqualTo(conversation.id)
		assertThat(messageRow[1]).isEqualTo(0L)
		assertThat(messageRow[2]).isEqualTo('user')
		assertThat(messageRow[6]).isEqualTo('completed') // default aplicado en la entidad
		assertThat(messageRow[9].toString()).contains('source', 'test')

		// El JSON se serializa y deserializa de forma idéntica
		def loaded = em.find(Message, message.id)
		assertThat(loaded.metadata).containsEntry('source', 'test')
		assertThat(loaded.metadata['temperature'].toString()).isEqualTo('0.2')
	}

	@Test
	void 'las consultas HQL resuelven todas las entidades'() {
		def user = new User(email: "hql-${UUID.randomUUID()}@example.com", displayName: 'HQL', passwordHash: 'x')
		em.persist(user)
		def conversation = new Conversation(userId: user.id, title: 'HQL')
		em.persist(conversation)
		em.persist(new Message(conversationId: conversation.id, position: 0L, role: 'user',
				content: 'hola', message: 'hola'))
		em.flush()

		assertThat(em.createQuery('select count(u) from User u where u.id = :id', Long)
				.setParameter('id', user.id)
				.singleResult).isEqualTo(1L)
		assertThat(em.createQuery('select count(c) from Conversation c where c.userId = :userId', Long)
				.setParameter('userId', user.id)
				.singleResult).isEqualTo(1L)
		assertThat(em.createQuery('select count(m) from Message m where m.conversationId = :cid and m.status = :status', Long)
				.setParameter('cid', conversation.id)
				.setParameter('status', 'completed')
				.singleResult).isEqualTo(1L)
	}
}
