package com.soudan.task.domain;

import java.time.Instant;

import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Bình luận trên một task, có thể là trả lời (một cấp) cho bình luận khác. Nội dung là văn bản thuần.
 * Xoá là xoá mềm và KHÔNG ẩn bằng bộ lọc tự động: bình luận cha đã xoá nhưng còn trả lời vẫn cần để hiện
 * "bình luận đã bị xoá" làm mốc cho các trả lời; tầng service quyết định cái nào được trả ra.
 */
@Entity
@Table(name = "task_comment")
public class TaskComment extends BaseEntity {

	public static final int MAX_LENGTH = 2000;

	@Column(nullable = false)
	private Long taskId;

	@Column(nullable = false)
	private Long authorId;

	/** Bình luận gốc mà bình luận này trả lời; null nếu là bình luận gốc. Luôn là bình luận gốc (chỉ một cấp). */
	private Long parentId;

	@Column(nullable = false)
	private String body;

	private Instant editedAt;

	protected TaskComment() {
		// JPA
	}

	public static TaskComment create(Long taskId, Long authorId, Long parentId, String body) {
		TaskComment comment = new TaskComment();
		comment.taskId = taskId;
		comment.authorId = authorId;
		comment.parentId = parentId;
		comment.body = clean(body);
		return comment;
	}

	/** Chỉ đổi nội dung; ai được sửa do service quyết định (chỉ tác giả). */
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

	public Long getTaskId() {
		return taskId;
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
