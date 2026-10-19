package com.ia.backend.chat.repository

import com.ia.backend.chat.entity.Message
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository extends JpaRepository<Message, UUID> {

	List<Message> findByConversationIdOrderByPositionAsc(UUID conversationId)

	long countByConversationId(UUID conversationId)

	/** Mayor position de la conversación; null si no hay messages. */
	@Query('select max(m.position) from Message m where m.conversationId = :conversationId')
	Long findMaxPosition(@Param('conversationId') UUID conversationId)
}
