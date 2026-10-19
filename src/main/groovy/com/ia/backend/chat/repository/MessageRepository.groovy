package com.ia.backend.chat.repository

import com.ia.backend.chat.entity.Message
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository extends JpaRepository<Message, UUID> {

	List<Message> findByConversationIdOrderByPositionAsc(UUID conversationId)

	long countByConversationId(UUID conversationId)
}
