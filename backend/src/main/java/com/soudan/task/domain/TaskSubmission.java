package com.soudan.task.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một file đã nộp cho task. {@code storageKey} là khoá ngẫu nhiên của FileStorage, không phải tên file. */
@Entity
@Table(name = "task_submission")
public class TaskSubmission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long taskId;

	@Column(nullable = false)
	private String fileName;

	@Column(nullable = false)
	private String contentType;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private String storageKey;

	@Column(nullable = false)
	private Long uploadedBy;

	@Column(nullable = false)
	private Instant uploadedAt;

	protected TaskSubmission() {
		// JPA
	}

	public TaskSubmission(Long taskId, String fileName, String contentType, long sizeBytes, String storageKey,
			Long uploadedBy, Instant uploadedAt) {
		this.taskId = taskId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = uploadedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getTaskId() {
		return taskId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public Long getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}
}
