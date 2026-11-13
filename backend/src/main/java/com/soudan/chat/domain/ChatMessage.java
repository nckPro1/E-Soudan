package com.soudan.chat.domain;

import java.time.Instant;

import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một tin nhắn. Nội dung là văn bản thuần (frontend luôn hiển thị dạng text, không dựng HTML). Tin KHÔNG bao giờ bị
 * xoá khỏi CSDL: "xoá" chỉ đặt {@code deletedAt} và ẩn nội dung khi hiển thị, để lịch sử được giữ lâu dài.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

	public static final int MAX_LENGTH = 4000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long conversationId;

	@Column(nullable = false)
	private Long authorId;

	@Column(columnDefinition = "text")
	private String body;

	private Long replyToId;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant editedAt;

	private Instant deletedAt;

	protected ChatMessage() {
		// JPA
	}

	public ChatMessage(Long conversationId, Long authorId, String body, Long replyToId, Instant createdAt) {
		this.conversationId = conversationId;
		this.authorId = authorId;
		this.body = clean(body);
		this.replyToId = replyToId;
		this.createdAt = createdAt;
	}

	/** Chuẩn hoá nội dung: bỏ khoảng trắng đầu cuối, rỗng thành null, kiểm tra độ dài. */
	public static String clean(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String value = text.strip();
		if (value.length() > MAX_LENGTH) {
			throw new ValidationException("Tin nhắn tối đa " + MAX_LENGTH + " ký tự");
		}
		return value;
	}

	/** Chỉ đổi chữ; tin đã xoá thì không sửa được và nội dung không được để trống. */
	public void edit(String newBody, Instant now) {
		String cleaned = clean(newBody);
		if (cleaned == null) {
			throw new ValidationException("Nội dung tin nhắn không được để trống");
		}
		if (!cleaned.equals(body)) {
			body = cleaned;
			editedAt = now;
		}
	}

	public void markDeleted(Instant now) {
		if (deletedAt == null) {
			deletedAt = now;
		}
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public Long getId() {
		return id;
	}

	public Long getConversationId() {
		return conversationId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}

	public Long getReplyToId() {
		return replyToId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEditedAt() {
		return editedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
