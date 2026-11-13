package com.soudan.notification.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một thông báo gửi cho đúng một người. Lưu dữ liệu có cấu trúc: loại, người gây ra, đối tượng liên quan (task hoặc
 * báo cáo) và vài chữ để hiển thị (tiêu đề, đoạn trích). Không lưu câu chữ hoàn chỉnh để giao diện dịch theo ngôn ngữ.
 */
@Entity
@Table(name = "notification")
public class Notification {

	private static final int MAX_TEXT = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	private Long actorId;

	private Long taskId;

	private Long communityId;

	private Long postId;

	/** Tác giả báo cáo: cần để dựng đường dẫn tới trang báo cáo riêng của người đó. */
	private Long postAuthorId;

	/** Cuộc trò chuyện liên quan (lời mời nhắn tin). */
	private Long conversationId;

	private String subject;

	private String snippet;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant readAt;

	protected Notification() {
		// JPA
	}

	private Notification(Long recipientId, NotificationType type, Long actorId, Instant createdAt) {
		this.recipientId = recipientId;
		this.type = type;
		this.actorId = actorId;
		this.createdAt = createdAt;
	}

	/** Thông báo về một task. */
	public static Notification forTask(Long recipientId, NotificationType type, Long actorId, Long taskId,
			String subject, String snippet, Instant at) {
		Notification n = new Notification(recipientId, type, actorId, at);
		n.taskId = taskId;
		n.subject = cut(subject);
		n.snippet = cut(snippet);
		return n;
	}

	/** Thông báo về một báo cáo (hoặc bình luận trên báo cáo). */
	public static Notification forReport(Long recipientId, NotificationType type, Long actorId, Long communityId,
			Long postId, Long postAuthorId, String subject, String snippet, Instant at) {
		Notification n = new Notification(recipientId, type, actorId, at);
		n.communityId = communityId;
		n.postId = postId;
		n.postAuthorId = postAuthorId;
		n.subject = cut(subject);
		n.snippet = cut(snippet);
		return n;
	}

	/** Thông báo về một cuộc trò chuyện (lời mời nhắn tin). */
	public static Notification forChat(Long recipientId, NotificationType type, Long actorId, Long conversationId,
			Instant at) {
		Notification n = new Notification(recipientId, type, actorId, at);
		n.conversationId = conversationId;
		return n;
	}

	public void markRead(Instant at) {
		if (readAt == null) {
			readAt = at;
		}
	}

	public boolean isRead() {
		return readAt != null;
	}

	private static String cut(String text) {
		if (text == null) {
			return null;
		}
		String value = text.strip();
		if (value.isEmpty()) {
			return null;
		}
		return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT - 1) + "…";
	}

	public Long getId() {
		return id;
	}

	public Long getRecipientId() {
		return recipientId;
	}

	public NotificationType getType() {
		return type;
	}

	public Long getActorId() {
		return actorId;
	}

	public Long getTaskId() {
		return taskId;
	}

	public Long getCommunityId() {
		return communityId;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getConversationId() {
		return conversationId;
	}

	public Long getPostAuthorId() {
		return postAuthorId;
	}

	public String getSubject() {
		return subject;
	}

	public String getSnippet() {
		return snippet;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}
}
