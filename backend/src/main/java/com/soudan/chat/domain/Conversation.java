package com.soudan.chat.domain;

import java.time.Instant;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một cuộc trò chuyện. Hai loại:
 * <ul>
 * <li>{@link ConversationType#DIRECT}: nhắn riêng giữa hai người. Bắt đầu bằng một LỜI MỜI (PENDING) do một bên gửi;
 * chỉ khi bên kia chấp nhận (ACCEPTED) hai bên mới nhắn được. Mỗi cặp chỉ có một cuộc trò chuyện (lưu id nhỏ trước).</li>
 * <li>{@link ConversationType#TEAM}: phòng chat chung của một nhóm; thành viên là thành viên nhóm, không lưu lại ở đây.</li>
 * </ul>
 */
@Entity
@Table(name = "chat_conversation")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConversationType type;

	private Long teamId;

	private Long userLowId;

	private Long userHighId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DirectStatus status;

	/** Người gửi lời mời (chỉ DIRECT). */
	private Long requestedBy;

	private Instant respondedAt;

	private Long lastMessageId;

	private Instant lastMessageAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected Conversation() {
		// JPA
	}

	/** Lời mời nhắn riêng từ {@code requesterId} tới {@code recipientId}. */
	public static Conversation request(Long requesterId, Long recipientId, Instant now) {
		Conversation c = new Conversation();
		c.type = ConversationType.DIRECT;
		c.userLowId = Math.min(requesterId, recipientId);
		c.userHighId = Math.max(requesterId, recipientId);
		c.status = DirectStatus.PENDING;
		c.requestedBy = requesterId;
		c.createdAt = now;
		return c;
	}

	public static Conversation forTeam(Long teamId, Instant now) {
		Conversation c = new Conversation();
		c.type = ConversationType.TEAM;
		c.teamId = teamId;
		c.status = DirectStatus.ACCEPTED;
		c.createdAt = now;
		return c;
	}

	public boolean isDirect() {
		return type == ConversationType.DIRECT;
	}

	public boolean isTeam() {
		return type == ConversationType.TEAM;
	}

	public boolean isParticipant(Long userId) {
		return isDirect() && (userId.equals(userLowId) || userId.equals(userHighId));
	}

	/** Người còn lại của cuộc nhắn riêng. */
	public Long otherOf(Long userId) {
		return userId.equals(userLowId) ? userHighId : userLowId;
	}

	/** Đối phương của người gửi lời mời, tức người phải trả lời. */
	public Long recipient() {
		return otherOf(requestedBy);
	}

	/** Bên nhận chấp nhận lời mời: từ đây hai bên nhắn được. */
	public void accept(Instant now) {
		requirePending();
		status = DirectStatus.ACCEPTED;
		respondedAt = now;
	}

	/** Bên nhận từ chối lời mời. */
	public void decline(Instant now) {
		requirePending();
		status = DirectStatus.DECLINED;
		respondedAt = now;
	}

	/** Bên đã từ chối đổi ý và chủ động mời lại: lời mời đổi chiều và quay về chờ trả lời. */
	public void reopenFrom(Long newRequesterId, Instant now) {
		if (status != DirectStatus.DECLINED) {
			throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ mời lại được khi lời mời đã bị từ chối");
		}
		requestedBy = newRequesterId;
		status = DirectStatus.PENDING;
		respondedAt = null;
	}

	private void requirePending() {
		if (status != DirectStatus.PENDING) {
			throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "Lời mời này không còn chờ trả lời");
		}
	}

	public void touch(Long messageId, Instant at) {
		lastMessageId = messageId;
		lastMessageAt = at;
	}

	public Long getId() {
		return id;
	}

	public ConversationType getType() {
		return type;
	}

	public Long getTeamId() {
		return teamId;
	}

	public Long getUserLowId() {
		return userLowId;
	}

	public Long getUserHighId() {
		return userHighId;
	}

	public DirectStatus getStatus() {
		return status;
	}

	public Long getRequestedBy() {
		return requestedBy;
	}

	public Long getLastMessageId() {
		return lastMessageId;
	}

	public Instant getLastMessageAt() {
		return lastMessageAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
