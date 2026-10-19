package com.ia.backend.chat.repository

import com.ia.backend.chat.entity.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	List<Conversation> findByUserIdOrderByCreatedAtDesc(UUID userId)
}
