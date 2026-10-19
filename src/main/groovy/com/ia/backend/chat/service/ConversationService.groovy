package com.ia.backend.chat.service

import com.ia.backend.chat.dto.ConversationResponse
import com.ia.backend.chat.dto.CreateConversationRequest
import com.ia.backend.chat.entity.Conversation
import com.ia.backend.chat.repository.ConversationRepository
import com.ia.backend.chat.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConversationService {

	private final ConversationRepository conversationRepository
	private final UserRepository userRepository

	ConversationService(ConversationRepository conversationRepository, UserRepository userRepository) {
		this.conversationRepository = conversationRepository
		this.userRepository = userRepository
	}

	@Transactional
	ConversationResponse create(CreateConversationRequest request) {
		if (!userRepository.existsById(request.userId)) {
			throw new UserNotFoundException(request.userId)
		}

		def conversation = new Conversation(
				userId: request.userId,
				title: request.title?.trim()
		)

		conversationRepository.saveAndFlush(conversation)
		ConversationResponse.from(conversation)
	}
}
