package com.soudan.task.domain;

import org.hibernate.annotations.SQLRestriction;

import com.soudan.common.base.BaseEntity;
import com.soudan.common.exception.ValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Bình luận trên một task. Nội dung là văn bản thuần; giao diện luôn hiển thị dạng text nên không chạy được mã. */
@Entity
@Table(name = "task_comment")
@SQLRestriction("deleted_at IS NULL")
public class TaskComment extends BaseEntity {

	public static final int MAX_LENGTH = 2000;

	@Column(nullable = false)
	private Long taskId;

	@Column(nullable = false)
	private Long authorId;

	@Column(nullable = false)
	private String body;

	protected TaskComment() {
		// JPA
	}

	public static TaskComment create(Long taskId, Long authorId, String body) {
		if (body == null || body.isBlank()) {
			throw new ValidationException("Nội dung bình luận không được để trống");
		}
		String text = body.strip();
		if (text.length() > MAX_LENGTH) {
			throw new ValidationException("Bình luận tối đa " + MAX_LENGTH + " ký tự");
		}
		TaskComment comment = new TaskComment();
		comment.taskId = taskId;
		comment.authorId = authorId;
		comment.body = text;
		return comment;
	}

	public Long getTaskId() {
		return taskId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}
}
