package com.ia.backend.chat.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table

import java.time.Instant

@Entity
@Table(name = "conversations")
class Conversation {

	@Id
	UUID id

	/** FK -> users.id (sin lookup, solo el identificador) */
	@Column(name = "user_id")
	UUID userId

	@Column
	String title

	@Column(name = "created_at", nullable = false, updatable = false)
	Instant createdAt

	@Column(name = "updated_at", nullable = false)
	Instant updatedAt

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID()
		}
		def now = Instant.now()
		if (createdAt == null) {
			createdAt = now
		}
		updatedAt = now
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now()
	}
}
