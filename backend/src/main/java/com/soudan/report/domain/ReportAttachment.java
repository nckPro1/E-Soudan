package com.soudan.report.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một file hoặc ảnh đính kèm báo cáo. {@code storageKey} là khoá ngẫu nhiên của FileStorage, không phải tên file. */
@Entity
@Table(name = "report_attachment")
public class ReportAttachment {

	/** Tối đa bao nhiêu file đính kèm cho một báo cáo. */
	public static final int MAX_PER_POST = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long postId;

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

	protected ReportAttachment() {
		// JPA
	}

	public ReportAttachment(Long postId, String fileName, String contentType, long sizeBytes, String storageKey,
			Long uploadedBy, Instant uploadedAt) {
		this.postId = postId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = uploadedAt;
	}

	public boolean isImage() {
		return contentType.startsWith("image/");
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
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
}
