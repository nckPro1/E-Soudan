package com.soudan.report.domain;

import java.time.Instant;

import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Bình luận trên một báo cáo, có thể là trả lời (một cấp). Xoá mềm và không ẩn tự động: bình luận cha đã xoá
 * nhưng còn trả lời vẫn cần làm mốc, tầng service quyết định cái nào được trả ra.
 */
@Entity
@Table(name = "report_comment")
public class ReportComment extends BaseEntity {

	public static final int MAX_LENGTH = 2000;

	@Column(nullable = false)
	private Long postId;

	@Column(nullable = false)
	private Long authorId;

	private Long parentId;

	@Column(nullable = false)
	private String body;

	private Instant editedAt;

	protected ReportComment() {
		// JPA
	}

	public static ReportComment create(Long postId, Long authorId, Long parentId, String body) {
		ReportComment comment = new ReportComment();
		comment.postId = postId;
		comment.authorId = authorId;
		comment.parentId = parentId;
		comment.body = clean(body);
		return comment;
	}

	public void edit(String newBody, Instant now) {
		String cleaned = clean(newBody);
		if (!cleaned.equals(body)) {
			this.body = cleaned;
			this.editedAt = now;
		}
	}

	private static String clean(String body) {
		if (body == null || body.isBlank()) {
			throw new ValidationException("Nội dung bình luận không được để trống");
		}
		String text = body.strip();
		if (text.length() > MAX_LENGTH) {
			throw new ValidationException("Bình luận tối đa " + MAX_LENGTH + " ký tự");
		}
		return text;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public Long getParentId() {
		return parentId;
	}

	public String getBody() {
		return body;
	}

	public Instant getEditedAt() {
		return editedAt;
	}
}
