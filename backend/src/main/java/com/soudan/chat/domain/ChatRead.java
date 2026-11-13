package com.soudan.chat.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Một người đã đọc tới tin nào của một cuộc trò chuyện. Số chưa đọc = số tin của người khác có id lớn hơn mốc này. */
@Entity
@Table(name = "chat_read")
public class ChatRead {

	@Embeddable
	public static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		@Column(name = "conversation_id")
		private Long conversationId;

		@Column(name = "user_id")
		private Long userId;

		protected Key() {
			// JPA
		}

		public Key(Long conversationId, Long userId) {
			this.conversationId = conversationId;
			this.userId = userId;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && Objects.equals(conversationId, k.conversationId) && Objects.equals(userId, k.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(conversationId, userId);
		}
	}

	@EmbeddedId
	private Key key;

	@Column(name = "last_read_message_id", nullable = false)
	private long lastReadMessageId;

	protected ChatRead() {
		// JPA
	}

	public ChatRead(Long conversationId, Long userId, long lastReadMessageId) {
		this.key = new Key(conversationId, userId);
		this.lastReadMessageId = lastReadMessageId;
	}

	/** Chỉ tiến, không bao giờ lùi. */
	public void advanceTo(long messageId) {
		if (messageId > lastReadMessageId) {
			lastReadMessageId = messageId;
		}
	}

	public long getLastReadMessageId() {
		return lastReadMessageId;
	}
}
