package com.ia.backend.chat.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

import java.time.Instant

@Entity
@Table(name = "messages")
class Message {

	@Id
	UUID id

	/** FK -> conversations.id (sin lookup, solo el identificador) */
	@Column(name = "conversation_id", nullable = false)
	UUID conversationId

	@Column(nullable = false)
	Long position

	/** CHECK: system | user | assistant | tool */
	@Column(nullable = false)
	String role

	@Column(nullable = false)
	String content

	@Column(nullable = false)
	String message

	@Column
	String model

	/** CHECK implícito por default: 'completed' */
	@Column(nullable = false)
	String status = 'completed'

	@Column(name = "input_tokens")
	Integer inputTokens

	@Column(name = "output_tokens")
	Integer outputTokens

	@Column(name = "created_at", nullable = false, updatable = false)
	Instant createdAt

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	Map<String, Object> metadata = [:]

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID()
		}
		if (createdAt == null) {
			createdAt = Instant.now()
		}
		if (metadata == null) {
			metadata = [:]
		}
	}
}
