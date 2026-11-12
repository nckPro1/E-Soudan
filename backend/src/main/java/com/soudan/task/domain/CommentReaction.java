package com.soudan.task.domain;

import java.time.Instant;

import com.soudan.common.api.ErrorCode;
import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một biểu cảm (emoji) của một người trên một bình luận. Duy nhất theo (bình luận, người, emoji). */
@Entity
@Table(name = "comment_reaction")
public class CommentReaction {

	/** Tối đa bao nhiêu emoji khác nhau trên một bình luận (chống lạm dụng). */
	public static final int MAX_DISTINCT_PER_COMMENT = 20;

	private static final int MAX_CODE_UNITS = 32;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long commentId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private String emoji;

	@Column(nullable = false)
	private Instant createdAt;

	protected CommentReaction() {
		// JPA
	}

	public CommentReaction(Long commentId, Long userId, String emoji, Instant createdAt) {
		this.commentId = commentId;
		this.userId = userId;
		this.emoji = requireEmoji(emoji);
		this.createdAt = createdAt;
	}

	/**
	 * Chỉ nhận chuỗi emoji thật (kể cả chuỗi ghép như gia đình, cờ, màu da, keycap); chữ thường, số trần, ký tự
	 * điều khiển hay HTML đều bị từ chối.
	 */
	public static String requireEmoji(String value) {
		if (value == null || value.isBlank() || value.length() > MAX_CODE_UNITS || !isEmojiSequence(value)) {
			throw new ValidationException(ErrorCode.INVALID_EMOJI, "Biểu cảm không hợp lệ");
		}
		return value;
	}

	private static boolean isEmojiSequence(String value) {
		boolean hasNonAscii = false;
		for (int i = 0; i < value.length();) {
			int cp = value.codePointAt(i);
			i += Character.charCount(cp);
			boolean joiner = cp == 0x200D || cp == 0xFE0F || cp == 0x20E3;
			boolean tag = cp >= 0xE0020 && cp <= 0xE007F;
			if (!joiner && !tag && !Character.isEmoji(cp)) {
				return false;
			}
			if (cp > 0x7F) {
				hasNonAscii = true;
			}
		}
		// Chữ số, # và * cũng mang thuộc tính emoji; chỉ hợp lệ khi là keycap (có FE0F/20E3), tức đã có ký tự > 0x7F.
		return hasNonAscii;
	}

	public Long getId() {
		return id;
	}

	public Long getCommentId() {
		return commentId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEmoji() {
		return emoji;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
