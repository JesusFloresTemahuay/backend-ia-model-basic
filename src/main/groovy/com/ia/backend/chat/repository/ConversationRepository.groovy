package com.ia.backend.chat.repository

import com.ia.backend.chat.entity.Conversation
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	List<Conversation> findByUserIdOrderByCreatedAtDesc(UUID userId)

	/**
	 * Carga la conversación con bloqueo pessimista (SELECT ... FOR UPDATE) para
	 * serializar la asignación de position de los messages de esa conversación.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query('select c from Conversation c where c.id = :id')
	Optional<Conversation> findByIdForUpdate(@Param('id') UUID id)
}
