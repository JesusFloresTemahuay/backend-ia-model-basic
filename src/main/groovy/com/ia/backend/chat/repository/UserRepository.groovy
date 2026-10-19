package com.ia.backend.chat.repository

import com.ia.backend.chat.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmail(String email)

	Optional<User> findByEmail(String email)
}
