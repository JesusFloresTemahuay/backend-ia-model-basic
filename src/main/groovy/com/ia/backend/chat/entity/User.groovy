package com.ia.backend.chat.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User {

	@Id
	UUID id

	@Column
	String email

	@Column(name = "display_name")
	String displayName

	@Column(name = "password_hash", nullable = false)
	String passwordHash

	@Column(name = "created_at", nullable = false, updatable = false)
	Instant createdAt

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID()
		}
		if (createdAt == null) {
			createdAt = Instant.now()
		}
	}
}
