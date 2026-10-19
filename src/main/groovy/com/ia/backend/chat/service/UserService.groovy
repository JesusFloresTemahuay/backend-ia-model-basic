package com.ia.backend.chat.service

import com.ia.backend.chat.dto.CreateUserRequest
import com.ia.backend.chat.dto.UserResponse
import com.ia.backend.chat.entity.User
import com.ia.backend.chat.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService {

	private final UserRepository userRepository
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder()

	UserService(UserRepository userRepository) {
		this.userRepository = userRepository
	}

	@Transactional
	UserResponse create(CreateUserRequest request) {
		def email = request.email.trim().toLowerCase()

		if (userRepository.existsByEmail(email)) {
			throw new DuplicateUserException(email)
		}

		def user = new User(
				email: email,
				displayName: request.displayName?.trim(),
				passwordHash: passwordEncoder.encode(request.password)
		)

		try {
			userRepository.saveAndFlush(user)
		} catch (DataIntegrityViolationException ignored) {
			// Carrera entre el existsByEmail y el insert: mapea la restricción única a 409
			throw new DuplicateUserException(email)
		}

		UserResponse.from(user)
	}

	@Transactional
	void delete(UUID id) {
		def user = userRepository.findById(id)
				.orElseThrow { new UserNotFoundException(id) }
		userRepository.delete(user)
	}
}
