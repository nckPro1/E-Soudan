package com.soudan.chat.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Biểu cảm (emoji) của một người trên một tin nhắn. Duy nhất theo (tin, người, emoji). */
@Entity
@Table(name = "chat_reaction")
public class ChatReaction {

	/** Tối đa bao nhiêu loại emoji khác nhau trên một tin nhắn (chống lạm dụng). */
	public static final int MAX_DISTINCT = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long messageId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private String emoji;

	@Column(nullable = false)
	private Instant createdAt;

	protected ChatReaction() {
		// JPA
	}

	public ChatReaction(Long messageId, Long userId, String emoji, Instant createdAt) {
		this.messageId = messageId;
		this.userId = userId;
		this.emoji = emoji;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getMessageId() {
		return messageId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEmoji() {
		return emoji;
	}
}
