package com.soudan.report.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Biểu cảm của một người trên một báo cáo (postId) hoặc một bình luận (commentId), đúng một trong hai. */
@Entity
@Table(name = "report_reaction")
public class ReportReaction {

	/** Tối đa bao nhiêu loại emoji khác nhau trên một mục (chống lạm dụng). */
	public static final int MAX_DISTINCT = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long postId;

	private Long commentId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private String emoji;

	@Column(nullable = false)
	private Instant createdAt;

	protected ReportReaction() {
		// JPA
	}

	public static ReportReaction onPost(Long postId, Long userId, String emoji, Instant at) {
		ReportReaction r = new ReportReaction();
		r.postId = postId;
		r.userId = userId;
		r.emoji = emoji;
		r.createdAt = at;
		return r;
	}

	public static ReportReaction onComment(Long commentId, Long userId, String emoji, Instant at) {
		ReportReaction r = new ReportReaction();
		r.commentId = commentId;
		r.userId = userId;
		r.emoji = emoji;
		r.createdAt = at;
		return r;
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
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
}
