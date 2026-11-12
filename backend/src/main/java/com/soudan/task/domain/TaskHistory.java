package com.soudan.task.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một lần đổi trạng thái. Chỉ thêm, không sửa: không có setter. */
@Entity
@Table(name = "task_history")
public class TaskHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long taskId;

	@Enumerated(EnumType.STRING)
	private TaskStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskStatus toStatus;

	@Column(nullable = false)
	private Long changedBy;

	@Column(nullable = false)
	private Instant changedAt;

	private String note;

	protected TaskHistory() {
		// JPA
	}

	public TaskHistory(Long taskId, TaskStatus fromStatus, TaskStatus toStatus, Long changedBy, Instant changedAt,
			String note) {
		this.taskId = taskId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
		this.changedAt = changedAt;
		this.note = note;
	}

	public Long getId() {
		return id;
	}

	public Long getTaskId() {
		return taskId;
	}

	public TaskStatus getFromStatus() {
		return fromStatus;
	}

	public TaskStatus getToStatus() {
		return toStatus;
	}

	public Long getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

	public String getNote() {
		return note;
	}
}
